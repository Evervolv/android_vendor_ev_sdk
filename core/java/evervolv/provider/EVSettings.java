/**
 * Copyright (C) 2015-2016 The CyanogenMod Project
 * Copyright (C) 2017-2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package evervolv.provider;

import com.android.internal.util.ArrayUtils;

import android.content.ContentResolver;
import android.content.IContentProvider;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * EVSettings contains Evervolv specific preferences in System, Secure, and Global.
 */
public final class EVSettings {
    private static final String TAG = "EVSettings";
    private static final boolean LOCAL_LOGV = false;

    public static final String AUTHORITY = "evsettings";

    public static class EVSettingNotFoundException extends AndroidException {
        public EVSettingNotFoundException(String msg) {
            super(msg);
        }
    }

    // region Call Methods

    /**
     * @hide - User handle argument extra to the fast-path call()-based requests
     */
    public static final String CALL_METHOD_USER_KEY = "_user";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'system' table.
     */
    public static final String CALL_METHOD_GET_SYSTEM = "GET_system";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'secure' table.
     */
    public static final String CALL_METHOD_GET_SECURE = "GET_secure";

    /**
     * @hide - Private call() method on SettingsProvider to read from 'global' table.
     */
    public static final String CALL_METHOD_GET_GLOBAL = "GET_global";

    /**
     * @hide - Private call() method to write to 'system' table
     */
    public static final String CALL_METHOD_PUT_SYSTEM = "PUT_system";

    /**
     * @hide - Private call() method to write to 'secure' table
     */
    public static final String CALL_METHOD_PUT_SECURE = "PUT_secure";

    /**
     * @hide - Private call() method to write to 'global' table
     */
    public static final String CALL_METHOD_PUT_GLOBAL= "PUT_global";

    /**
     * @hide - Private call() method on EVSettingsProvider to migrate Evervolv settings
     */
    public static final String CALL_METHOD_MIGRATE_SETTINGS = "migrate_settings";

    /**
     * @hide - Private call() method on EVSettingsProvider to migrate Evervolv settings for a user
     */
    public static final String CALL_METHOD_MIGRATE_SETTINGS_FOR_USER = "migrate_settings_for_user";

    /**
     * @hide - Private call() method to list the entire system table
     */
    public static final String CALL_METHOD_LIST_SYSTEM = "LIST_system";

    /**
     * @hide - Private call() method to list the entire secure table
     */
    public static final String CALL_METHOD_LIST_SECURE = "LIST_secure";

    /**
     * @hide - Private call() method to list the entire global table
     */
    public static final String CALL_METHOD_LIST_GLOBAL = "LIST_global";

    /**
     * @hide - Private call() method to delete an entry from the system table
     */
    public static final String CALL_METHOD_DELETE_SYSTEM = "DELETE_system";

    /**
     * @hide - Private call() method to delete an entry from the secure table
     */
    public static final String CALL_METHOD_DELETE_SECURE = "DELETE_secure";

    /**
     * @hide - Private call() method to delete an entry from the global table
     */
    public static final String CALL_METHOD_DELETE_GLOBAL = "DELETE_global";

    // endregion

    // Thread-safe.
    private static class NameValueCache {
        private final String mVersionSystemProperty;
        private final Uri mUri;

        private static final String[] SELECT_VALUE_PROJECTION =
                new String[] { Settings.NameValueTable.VALUE };
        private static final String NAME_EQ_PLACEHOLDER = "name=?";

        // Must synchronize on 'this' to access mValues and mValuesVersion.
        private final HashMap<String, String> mValues = new HashMap<String, String>();
        private long mValuesVersion = 0;

        // Initially null; set lazily and held forever.  Synchronized on 'this'.
        private IContentProvider mContentProvider = null;

        // The method we'll call (or null, to not use) on the provider
        // for the fast path of retrieving settings.
        private final String mCallGetCommand;
        private final String mCallSetCommand;

        public NameValueCache(String versionSystemProperty, Uri uri,
                String getCommand, String setCommand) {
            mVersionSystemProperty = versionSystemProperty;
            mUri = uri;
            mCallGetCommand = getCommand;
            mCallSetCommand = setCommand;
        }

        private IContentProvider lazyGetProvider(ContentResolver cr) {
            IContentProvider cp;
            synchronized (this) {
                cp = mContentProvider;
                if (cp == null) {
                    cp = mContentProvider = cr.acquireProvider(mUri.getAuthority());
                }
            }
            return cp;
        }

        /**
         * Puts a string name/value pair into the content provider for the specified user.
         * @param cr The content resolver to use.
         * @param name The name of the key to put into the content provider.
         * @param value The value to put into the content provider.
         * @param userId The user id to use for the content provider.
         * @return Whether the put was successful.
         */
        public boolean putStringForUser(ContentResolver cr, String name, String value,
                final int userId) {
            try {
                Bundle arg = new Bundle();
                arg.putString(Settings.NameValueTable.VALUE, value);
                arg.putInt(CALL_METHOD_USER_KEY, userId);
                IContentProvider cp = lazyGetProvider(cr);
                cp.call(cr.getPackageName(), AUTHORITY, mCallSetCommand, name, arg);
            } catch (RemoteException e) {
                Log.w(TAG, "Can't set key " + name + " in " + mUri, e);
                return false;
            }
            return true;
        }

        /**
         * Gets a string value with the specified name from the name/value cache if possible. If
         * not, it will use the content resolver and perform a query.
         * @param cr Content resolver to use if name/value cache does not contain the name or if
         *           the cache version is older than the current version.
         * @param name The name of the key to search for.
         * @param userId The user id of the cache to look in.
         * @return The string value of the specified key.
         */
        public String getStringForUser(ContentResolver cr, String name, final int userId) {
            final boolean isSelf = (userId == UserHandle.myUserId());
            if (isSelf) {
                if (LOCAL_LOGV) Log.d(TAG, "get setting for self");
                long newValuesVersion = SystemProperties.getLong(mVersionSystemProperty, 0);

                // Our own user's settings data uses a client-side cache
                synchronized (this) {
                    if (mValuesVersion != newValuesVersion) {
                        if (LOCAL_LOGV || false) {
                            Log.v(TAG, "invalidate [" + mUri.getLastPathSegment() + "]: current "
                                    + newValuesVersion + " != cached " + mValuesVersion);
                        }

                        mValues.clear();
                        mValuesVersion = newValuesVersion;
                    }

                    if (mValues.containsKey(name)) {
                        return mValues.get(name);  // Could be null, that's OK -- negative caching
                    }
                }
            } else {
                if (LOCAL_LOGV) Log.v(TAG, "get setting for user " + userId
                        + " by user " + UserHandle.myUserId() + " so skipping cache");
            }

            IContentProvider cp = lazyGetProvider(cr);

            // Try the fast path first, not using query().  If this
            // fails (alternate Settings provider that doesn't support
            // this interface?) then we fall back to the query/table
            // interface.
            if (mCallGetCommand != null) {
                try {
                    Bundle args = null;
                    if (!isSelf) {
                        args = new Bundle();
                        args.putInt(CALL_METHOD_USER_KEY, userId);
                    }
                    Bundle b = cp.call(cr.getPackageName(), AUTHORITY, mCallGetCommand, name, args);
                    if (b != null) {
                        String value = b.getPairValue();
                        // Don't update our cache for reads of other users' data
                        if (isSelf) {
                            synchronized (this) {
                                mValues.put(name, value);
                            }
                        } else {
                            if (LOCAL_LOGV) Log.i(TAG, "call-query of user " + userId
                                    + " by " + UserHandle.myUserId()
                                    + " so not updating cache");
                        }
                        return value;
                    }
                    // If the response Bundle is null, we fall through
                    // to the query interface below.
                } catch (RemoteException e) {
                    // Not supported by the remote side?  Fall through
                    // to query().
                }
            }

            Cursor c = null;
            try {
                Bundle queryArgs = ContentResolver.createSqlQueryBundle(
                        NAME_EQ_PLACEHOLDER, new String[]{name}, null);
                c = cp.query(cr.getPackageName(), mUri, SELECT_VALUE_PROJECTION, queryArgs, null);
                if (c == null) {
                    Log.w(TAG, "Can't get key " + name + " from " + mUri);
                    return null;
                }

                String value = c.moveToNext() ? c.getString(0) : null;
                synchronized (this) {
                    mValues.put(name, value);
                }
                if (LOCAL_LOGV) {
                    Log.v(TAG, "cache miss [" + mUri.getLastPathSegment() + "]: " +
                            name + " = " + (value == null ? "(null)" : value));
                }
                return value;
            } catch (RemoteException e) {
                Log.w(TAG, "Can't get key " + name + " from " + mUri, e);
                return null;  // Return null, but don't cache it.
            } finally {
                if (c != null) c.close();
            }
        }
    }

    // region Validators

    /** @hide */
    public static interface Validator {
        public boolean validate(String value);
    }

    private static final Validator sBooleanValidator =
            new DiscreteValueValidator(new String[] {"0", "1"});

    private static final Validator sNonNegativeIntegerValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            try {
                return Integer.parseInt(value) >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    };

    private static final Validator sUriValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            try {
                Uri.decode(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    };

    private static final Validator sColorValidator =
            new InclusiveIntegerRangeValidator(Integer.MIN_VALUE, Integer.MAX_VALUE);

    /**
     * Action to perform when a key is pressed.
     * 0 - Nothing
     * 1 - Menu
     * 2 - App-switch
     * 3 - Search
     * 4 - Voice search
     * 5 - In-app search
     * 6 - Launch Camera
     * 7 - Action Sleep
     * 8 - Last app
     * 9 - Toggle split screen
     */
    private static final Validator sActionValidator =
            new InclusiveIntegerRangeValidator(0, 9);

    private static final Validator sAlwaysTrueValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            return true;
        }
    };

    private static final Validator sNonNullStringValidator = new Validator() {
        @Override
        public boolean validate(String value) {
            return value != null;
        }
    };

    private static final class DiscreteValueValidator implements Validator {
        private final String[] mValues;

        public DiscreteValueValidator(String[] values) {
            mValues = values;
        }

        @Override
        public boolean validate(String value) {
            return ArrayUtils.contains(mValues, value);
        }
    }

    private static final class InclusiveIntegerRangeValidator implements Validator {
        private final int mMin;
        private final int mMax;

        public InclusiveIntegerRangeValidator(int min, int max) {
            mMin = min;
            mMax = max;
        }

        @Override
        public boolean validate(String value) {
            try {
                final int intValue = Integer.parseInt(value);
                return intValue >= mMin && intValue <= mMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static final class InclusiveFloatRangeValidator implements Validator {
        private final float mMin;
        private final float mMax;

        public InclusiveFloatRangeValidator(float min, float max) {
            mMin = min;
            mMax = max;
        }

        @Override
        public boolean validate(String value) {
            try {
                final float floatValue = Float.parseFloat(value);
                return floatValue >= mMin && floatValue <= mMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static final class DelimitedListValidator implements Validator {
        private final ArraySet<String> mValidValueSet;
        private final String mDelimiter;
        private final boolean mAllowEmptyList;

        public DelimitedListValidator(String[] validValues, String delimiter,
                                      boolean allowEmptyList) {
            mValidValueSet = new ArraySet<String>(Arrays.asList(validValues));
            mDelimiter = delimiter;
            mAllowEmptyList = allowEmptyList;
        }

        @Override
        public boolean validate(String value) {
            ArraySet<String> values = new ArraySet<String>();
            if (!TextUtils.isEmpty(value)) {
                final String[] array = TextUtils.split(value, Pattern.quote(mDelimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    values.add(item);
                }
            }
            if (values.size() > 0) {
                values.removeAll(mValidValueSet);
                // values.size() will be non-zero if it contains any values not in
                // mValidValueSet
                return values.size() == 0;
            } else if (mAllowEmptyList) {
                return true;
            }

            return false;
        }
    }
    // endregion Validators

    /**
     * System settings, containing miscellaneous Evervolv system preferences. This table holds simple
     * name/value pairs. There are convenience functions for accessing individual settings entries.
     */
    public static final class System extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/system");

        public static final String SYS_PROP_SETTING_VERSION = "sys.evervolv_settings_system_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SYSTEM,
                CALL_METHOD_PUT_SYSTEM);

        /** @hide */
        protected static final ArraySet<String> MOVED_TO_SECURE;
        static {
            MOVED_TO_SECURE = new ArraySet<>(1);
        }

        // region Methods

        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from EVSettings.System"
                        + " to EVSettings.Secure, value is unchanged.");
                return EVSettings.Secure.getStringForUser(resolver, name, userId);
            }
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
               int userId) {
            if (MOVED_TO_SECURE.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from EVSettings.System"
                        + " to EVSettings.Secure, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new EVSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // System Settings start

        /**
         * List of long-screen apps.
         */
        public static final String LONG_SCREEN_APPS = "long_screen_apps";

        /** @hide */
        public static final Validator LONG_SCREEN_APPS_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * Check the proximity sensor during wakeup
         * 0 = 0ff, 1 = on
         * @hide
         */
        public static final String PROXIMITY_ON_WAKE = "proximity_on_wake";

        /** @hide */
        public static final Validator PROXIMITY_ON_WAKE_VALIDATOR = sBooleanValidator;

         /**
         * Whether or not to vibrate when a touchscreen gesture is detected
         * @hide
         */
        public static final String TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK = "touchscreen_gesture_haptic_feedback";

        /** @hide */
        public static final Validator TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR = sBooleanValidator;

        /**
         * Action to perform when the home key is long-pressed.
         * (Default can be configured via config_longPressOnHomeBehavior)
         * 0 - Nothing
         * 1 - Menu
         * 2 - App-switch
         * 3 - Search
         * 4 - Voice search
         * 5 - In-app search
         * 6 - Launch Camera
         * 7 - Action Sleep
         * 8 - Last app
         * 9 - Toggle split screen
         * @hide
         */
        public static final String KEY_HOME_LONG_PRESS_ACTION = "key_home_long_press_action";

        /** @hide */
        public static final Validator KEY_HOME_LONG_PRESS_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the home key is double-tapped.
         * (Default can be configured via config_doubleTapOnHomeBehavior)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_HOME_DOUBLE_TAP_ACTION = "key_home_double_tap_action";

        /** @hide */
        public static final Validator KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the menu key is pressed. (Default is 1)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_MENU_ACTION = "key_menu_action";

        /** @hide */
        public static final Validator KEY_MENU_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the menu key is long-pressed.
         * (Default is 0 on devices with a search key, 3 on devices without)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_MENU_LONG_PRESS_ACTION = "key_menu_long_press_action";

        /** @hide */
        public static final Validator KEY_MENU_LONG_PRESS_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the assistant (search) key is pressed. (Default is 3)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_ASSIST_ACTION = "key_assist_action";

        /** @hide */
        public static final Validator KEY_ASSIST_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the assistant (search) key is long-pressed. (Default is 4)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_ASSIST_LONG_PRESS_ACTION = "key_assist_long_press_action";

        /** @hide */
        public static final Validator KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the app switch key is pressed. (Default is 2)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_APP_SWITCH_ACTION = "key_app_switch_action";

        /** @hide */
        public static final Validator KEY_APP_SWITCH_ACTION_VALIDATOR = sActionValidator;

         /**
         * Action to perform when the app switch key is long-pressed. (Default is 0)
         * (See KEY_HOME_LONG_PRESS_ACTION for valid values)
         * @hide
         */
        public static final String KEY_APP_SWITCH_LONG_PRESS_ACTION = "key_app_switch_long_press_action";

        /** @hide */
        public static final Validator KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR = sActionValidator;

        /**
         * Whether or not volume button music controls should be enabled to seek media tracks
         * 0 = 0ff, 1 = on
         * @hide
         */
        public static final String VOLBTN_MUSIC_CONTROLS = "volbtn_music_controls";

        /** @hide */
        public static final Validator VOLBTN_MUSIC_CONTROLS_VALIDATOR = sBooleanValidator;

        /**
         * Whether to wake the screen with the home key, the value is boolean.
         * @hide
         */
        public static final String HOME_WAKE_SCREEN = "home_wake_screen";

        /** @hide */
        public static final Validator HOME_WAKE_SCREEN_VALIDATOR = sBooleanValidator;
         /**
         * Whether to wake the screen with the back key, the value is boolean.
         * @hide
         */
        public static final String BACK_WAKE_SCREEN = "back_wake_screen";

        /** @hide */
        public static final Validator BACK_WAKE_SCREEN_VALIDATOR = sBooleanValidator;
         /**
         * Whether to wake the screen with the menu key, the value is boolean.
         * @hide
         */
        public static final String MENU_WAKE_SCREEN = "menu_wake_screen";

        /** @hide */
        public static final Validator MENU_WAKE_SCREEN_VALIDATOR = sBooleanValidator;
         /**
         * Whether to wake the screen with the assist key, the value is boolean.
         * @hide
         */
        public static final String ASSIST_WAKE_SCREEN = "assist_wake_screen";

        /** @hide */
        public static final Validator ASSIST_WAKE_SCREEN_VALIDATOR = sBooleanValidator;
         /**
         * Whether to wake the screen with the app switch key, the value is boolean.
         * @hide
         */
        public static final String APP_SWITCH_WAKE_SCREEN = "app_switch_wake_screen";

        /** @hide */
        public static final Validator APP_SWITCH_WAKE_SCREEN_VALIDATOR = sBooleanValidator;

         /**
         * Whether to wake the screen with the camera key, the value is boolean.
         * @hide
         */
        public static final String CAMERA_WAKE_SCREEN = "camera_wake_screen";

        /** @hide */
        public static final Validator CAMERA_WAKE_SCREEN_VALIDATOR = sBooleanValidator;

         /**
         * Whether to wake the screen with the volume keys, the value is boolean.
         * @hide
         */
        public static final String VOLUME_WAKE_SCREEN = "volume_wake_screen";

        /** @hide */
        public static final Validator VOLUME_WAKE_SCREEN_VALIDATOR = sBooleanValidator;

       /**
         * Enable looking up of phone numbers of nearby places
         *
         * @hide
         */
        public static final String ENABLE_FORWARD_LOOKUP = "enable_forward_lookup";

        /** @hide */
        public static final Validator ENABLE_FORWARD_LOOKUP_VALIDATOR = sBooleanValidator;

        /**
         * Enable looking up of phone numbers of people
         *
         * @hide
         */
        public static final String ENABLE_PEOPLE_LOOKUP = "enable_people_lookup";

        /** @hide */
        public static final Validator ENABLE_PEOPLE_LOOKUP_VALIDATOR = sBooleanValidator;

        /**
         * Enable looking up of information of phone numbers not in the contacts
         *
         * @hide
         */
        public static final String ENABLE_REVERSE_LOOKUP = "enable_reverse_lookup";

        /** @hide */
        public static final Validator ENABLE_REVERSE_LOOKUP_VALIDATOR = sBooleanValidator;

        /**
         * The forward lookup provider
         *
         * @hide
         */
        public static final String FORWARD_LOOKUP_PROVIDER = "forward_lookup_provider";

        /** @hide */
        public static final Validator FORWARD_LOOKUP_PROVIDER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * The people lookup provider
         *
         * @hide
         */
        public static final String PEOPLE_LOOKUP_PROVIDER = "people_lookup_provider";

        /** @hide */
        public static final Validator PEOPLE_LOOKUP_PROVIDER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * The reverse lookup provider
         *
         * @hide
         */
        public static final String REVERSE_LOOKUP_PROVIDER = "reverse_lookup_provider";

        /** @hide */
        public static final Validator REVERSE_LOOKUP_PROVIDER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * The OpenCNAM paid account ID
         *
         * @hide
         */
        public static final String DIALER_OPENCNAM_ACCOUNT_SID = "dialer_opencnam_account_sid";

        /** @hide */
        public static final Validator DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * The OpenCNAM authentication token
         *
         * @hide
         */
        public static final String DIALER_OPENCNAM_AUTH_TOKEN = "dialer_opencnam_auth_token";

        /** @hide */
        public static final Validator DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * Display style of the status bar battery information
         * 0: Display the battery an icon in portrait mode
         * 2: Display the battery as a circle
         * 3: Display the battery as a dotted circle
         * 4: Hide the battery status information
         * 5: Display the battery as plain text
         * default: 0
         * @hide
         */
        public static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";

        /** @hide */
        public static final Validator STATUS_BAR_BATTERY_STYLE_VALIDATOR =
            new InclusiveIntegerRangeValidator(0, 6);

        /**
         * Whether to use dark theme
         * 0: automatic - based on wallpaper
         * 1: time - based on LiveDisplay status
         * 2: force light
         * 3: force dark
         */
        public static final String BERRY_GLOBAL_STYLE = "berry_global_style";

        /** @hide */
        public static final Validator BERRY_GLOBAL_STYLE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Current accent package name
         */
        public static final String BERRY_CURRENT_ACCENT = "berry_current_accent";

        /** @hide */
        public static final Validator BERRY_CURRENT_ACCENT_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Current dark overlay package name
         */
        public static final String BERRY_DARK_OVERLAY = "berry_dark_overlay";

        /** @hide */
        public static final Validator BERRY_DARK_OVERLAY_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Current application managing the style
         */
        public static final String BERRY_MANAGED_BY_APP = "berry_managed_by_app";

        /** @hide */
        public static final Validator BERRY_MANAGED_BY_APP_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Whether to use black theme for dark mode
         */
        public static final String BERRY_BLACK_THEME = "berry_black_theme";

        /** @hide */
        public static final Validator BERRY_BLACK_THEME_VALIDATOR =
                sBooleanValidator;

        /**
         * Display style of AM/PM next to clock in status bar
         * 0: Normal display (Eclair stock)
         * 1: Small display (Froyo stock)
         * 2: No display (Gingerbread/ICS stock)
         * default: 2
         */
        public static final String STATUS_BAR_AM_PM = "status_bar_am_pm";

        /** @hide */
        public static final Validator STATUS_BAR_AM_PM_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

        /**
         * Whether to allow one finger quick settings expansion on the side of the statusbar.
         * 0 = 0ff, 1 = right, 2 = left
         */
        public static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";

        /** @hide */
        public static final Validator STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 2);

         /**
         * Whether the HighTouchSensitivity is activated or not.
         * 0 = off, 1 = on
         * @hide
         */
        public static final String HIGH_TOUCH_SENSITIVITY_ENABLE =
                "high_touch_sensitivity_enable";

        /** @hide */
        public static final Validator HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR = sBooleanValidator;

        /**
         * Swipe to screenshot
         * @hide
         */
        public static final String SWIPE_TO_SCREENSHOT = "swipe_to_screenshot";

        /** @hide */
        public static final Validator SWIPE_TO_SCREENSHOT_VALIDATOR = sBooleanValidator;

        /**
         * The button brightness to be used while the screen is on or after a button press,
         * depending on the value of {@link BUTTON_BACKLIGHT_TIMEOUT}.
         * Valid value range is between 0 and {@link PowerManager#getMaximumButtonBrightness()}
         * @hide
         */
        public static final String BUTTON_BRIGHTNESS = "button_brightness";

        /** @hide */
        public static final Validator BUTTON_BRIGHTNESS_VALIDATOR =
                new InclusiveIntegerRangeValidator(1, 255);

        /**
         * The time in ms to keep the button backlight on after pressing a button.
         * A value of 0 will keep the buttons on for as long as the screen is on.
         * @hide
         */
        public static final String BUTTON_BACKLIGHT_TIMEOUT = "button_backlight_timeout";

        /** @hide */
        public static final Validator BUTTON_BACKLIGHT_TIMEOUT_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * Contains the notifications light maximum brightness to use.
         * Values range from 1 to 255
         */
        public static final String NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL =
                "notification_light_brightness_level";

        /**
         * Contains the notifications light maximum brightness to use when Do Not
         * Disturb is active.
         * Values range from 1 to 255
         */
        public static final String NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_ZEN =
                "notification_light_brightness_level_zen";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR =
                new InclusiveIntegerRangeValidator(1, 255);

        /**
         * Whether to allow notifications with the screen on or DayDreams.
         * The value is boolean (1 or 0). Default will always be false.
         */
        public static final String NOTIFICATION_LIGHT_SCREEN_ON =
                "notification_light_screen_on_enable";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR =
                sBooleanValidator;

        /**
         * What color to use for the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR =
                "notification_light_pulse_default_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON =
                "notification_light_pulse_default_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the notification LED by default
         */
        public static final String NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF =
                "notification_light_pulse_default_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * What color to use for the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_COLOR =
                "notification_light_pulse_call_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_ON =
                "notification_light_pulse_call_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the missed call notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF =
                "notification_light_pulse_call_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * What color to use for the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR =
                "notification_light_pulse_vmail_color";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * How long to flash the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON =
                "notification_light_pulse_vmail_led_on";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * How long to wait between flashes for the voicemail notification LED
         */
        public static final String NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF =
                "notification_light_pulse_vmail_led_off";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR =
                sNonNegativeIntegerValidator;

        /**
         * Whether to use the custom LED values for the notification pulse LED.
         * 0 = 0ff, 1 = on
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE =
                "notification_light_pulse_custom_enable";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR =
                sBooleanValidator;

        /**
         * Which custom LED values to use for the notification pulse LED.
         */
        public static final String NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES =
                "notification_light_pulse_custom_values";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR =
                new Validator() {
                    @Override
                    public boolean validate(String value) {
                        if (TextUtils.isEmpty(value)) {
                            return true;
                        }

                        for (String packageValuesString : value.split("\\|")) {
                            String[] packageValues = packageValuesString.split("=");
                            if (packageValues.length != 2) {
                                if (LOCAL_LOGV) {
                                    Log.d(TAG, "Incorrect number of package values: "
                                            + packageValues.length);
                                }
                                return false;
                            }
                            String packageName = packageValues[0];
                            if (TextUtils.isEmpty(packageName)) {
                                if (LOCAL_LOGV)  Log.d(TAG, "Empty package name");
                                return false;
                            }
                            String[] values = packageValues[1].split(";");
                            if (values.length != 3) {
                                if (LOCAL_LOGV) {
                                    Log.d(TAG, "Incorrect number of values: " + values.length);
                                }
                                return false;
                            }
                            try {
                                // values[0] is LED color
                                if (!sColorValidator.validate(values[0])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED color (" + values[0] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                                // values[1] is the LED on time and should be non-negative
                                if (!sNonNegativeIntegerValidator.validate(values[1])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED on time (" + values[1] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                                // values[1] is the LED off time and should be non-negative
                                if (!sNonNegativeIntegerValidator.validate(values[2])) {
                                    if (LOCAL_LOGV) {
                                        Log.d(TAG, "Invalid LED off time (" + values[2] + ") for "
                                                + packageName);
                                    }
                                    return false;
                                }
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        // if we make it all the way through then the data is considered valid
                        return true;
                    }
                };

        /**
         * Whether we automatically generate notification LED colors or just
         * use the boring default.
         *
         * @hide
         */
        public static final String NOTIFICATION_LIGHT_COLOR_AUTO =
                "notification_light_color_auto";

        /** @hide */
        public static final Validator NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether the battery light should be enabled (if hardware supports it)
         * The value is boolean (1 or 0).
         */
        public static final String BATTERY_LIGHT_ENABLED = "battery_light_enabled";

        /** @hide */
        public static final Validator BATTERY_LIGHT_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether the battery LED should repeatedly flash when the battery is low
         * on charge. The value is boolean (1 or 0).
         */
        public static final String BATTERY_LIGHT_PULSE = "battery_light_pulse";

        /** @hide */
        public static final Validator BATTERY_LIGHT_PULSE_VALIDATOR =
                sBooleanValidator;

        /**
         * What color to use for the battery LED while charging - low
         */
        public static final String BATTERY_LIGHT_LOW_COLOR = "battery_light_low_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_LOW_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * What color to use for the battery LED while charging - medium
         */
        public static final String BATTERY_LIGHT_MEDIUM_COLOR = "battery_light_medium_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * What color to use for the battery LED while charging - full
         */
        public static final String BATTERY_LIGHT_FULL_COLOR = "battery_light_full_color";

        /** @hide */
        public static final Validator BATTERY_LIGHT_FULL_COLOR_VALIDATOR =
                sColorValidator;

        /**
         * Contains the battery light maximum brightness to use.
         * Values range from 1 to 255
         */
        public static final String BATTERY_LIGHT_BRIGHTNESS_LEVEL =
                "battery_light_brightness_level";

        /**
         * Contains the battery light maximum brightness to use when Do Not
         * Disturb is active.
         * Values range from 1 to 255
         */
        public static final String BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN =
                "battery_light_brightness_level_zen";

        /** @hide */
        public static final Validator BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR =
                new InclusiveIntegerRangeValidator(1, 255);

        /**
         * Whether the notification light will be allowed when in zen mode during downtime
         */
        public static final String ZEN_ALLOW_LIGHTS = "allow_lights";

        /** @hide */
        public static final Validator ZEN_ALLOW_LIGHTS_VALIDATOR = sBooleanValidator;

        /**
         * Whether the notification light will be allowed when in zen priority mode during downtime
         */
        public static final String ZEN_PRIORITY_ALLOW_LIGHTS = "zen_priority_allow_lights";

        /** @hide */
        public static final Validator ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR = sBooleanValidator;


        /**
         * Whether user can swap the order of the Alert Slider.
         * * Whether user can invert the order of the Alert Slider.
         * 0: Default
         * 1: Inverted
         * @hide
         */
        public static final String ALERT_SLIDER_ORDER = "alert_slider_order";

        /* @hide */
        public static final Validator ALERT_SLIDER_ORDER_VALIDATOR =
                sBooleanValidator;

        /**
         * Preferred silent mode for Alert Slider..
         * 0: Alarms only.
         * 1: Total silence
         * @hide
         */
        public static final String ALERT_SLIDER_SILENT_MODE = "alert_slider_on_left";

        /* @hide */
        public static final Validator ALERT_SLIDER_SILENT_MODE_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether or not to send device back to sleep if Camera button is released ("Peek")
         * @hide
         */
        public static final String CAMERA_SLEEP_ON_RELEASE = "camera_sleep_on_release";

        /** @hide */
        public static final Validator CAMERA_SLEEP_ON_RELEASE_VALIDATOR = sBooleanValidator;

        /**
         * Whether to launch secure camera app when key is longpressed
         * @hide
         */
        public static final String CAMERA_LAUNCH = "camera_launch";

        /** @hide */
        public static final Validator CAMERA_LAUNCH_VALIDATOR = sBooleanValidator;

        // System Settings end

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * Don't
         * @hide
         * me bro
         */
        public static final Validator __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * @hide
         */
        public static final String[] LEGACY_SYSTEM_SETTINGS = new String[] {
            // Insert legacy system settings here
            EVSettings.System.PROXIMITY_ON_WAKE,
            EVSettings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK,
            EVSettings.System.KEY_HOME_LONG_PRESS_ACTION,
            EVSettings.System.KEY_HOME_DOUBLE_TAP_ACTION,
            EVSettings.System.KEY_MENU_ACTION,
            EVSettings.System.KEY_MENU_LONG_PRESS_ACTION,
            EVSettings.System.KEY_ASSIST_ACTION,
            EVSettings.System.KEY_ASSIST_LONG_PRESS_ACTION,
            EVSettings.System.KEY_APP_SWITCH_ACTION,
            EVSettings.System.KEY_APP_SWITCH_LONG_PRESS_ACTION,
            EVSettings.System.VOLBTN_MUSIC_CONTROLS,
            EVSettings.System.HOME_WAKE_SCREEN,
            EVSettings.System.BACK_WAKE_SCREEN,
            EVSettings.System.MENU_WAKE_SCREEN,
            EVSettings.System.ASSIST_WAKE_SCREEN,
            EVSettings.System.APP_SWITCH_WAKE_SCREEN,
            EVSettings.System.CAMERA_WAKE_SCREEN,
            EVSettings.System.VOLUME_WAKE_SCREEN,
            EVSettings.System.ENABLE_FORWARD_LOOKUP,
            EVSettings.System.ENABLE_PEOPLE_LOOKUP,
            EVSettings.System.ENABLE_REVERSE_LOOKUP,
            EVSettings.System.FORWARD_LOOKUP_PROVIDER,
            EVSettings.System.PEOPLE_LOOKUP_PROVIDER,
            EVSettings.System.REVERSE_LOOKUP_PROVIDER,
            EVSettings.System.DIALER_OPENCNAM_ACCOUNT_SID,
            EVSettings.System.DIALER_OPENCNAM_AUTH_TOKEN,
            EVSettings.System.STATUS_BAR_AM_PM,
            EVSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_SYSTEM_SETTINGS, key);
        }

        /**
         * Mapping of validators for all system settings.  This map is used to validate both valid
         * keys as well as validating the values for those keys.
         *
         * Note: Make sure if you add a new System setting you create a Validator for it and add
         *       it to this map.
         *
         * @hide
         */
        public static final Map<String, Validator> VALIDATORS =
                new ArrayMap<String, Validator>();
        static {
            VALIDATORS.put(LONG_SCREEN_APPS, LONG_SCREEN_APPS_VALIDATOR);
            VALIDATORS.put(PROXIMITY_ON_WAKE, PROXIMITY_ON_WAKE_VALIDATOR);
            VALIDATORS.put(TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK,
                    TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK_VALIDATOR);
            VALIDATORS.put(KEY_HOME_LONG_PRESS_ACTION,
                    KEY_HOME_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_HOME_DOUBLE_TAP_ACTION,
                    KEY_HOME_DOUBLE_TAP_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_MENU_ACTION, KEY_MENU_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_MENU_LONG_PRESS_ACTION,
                    KEY_MENU_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_ASSIST_ACTION, KEY_ASSIST_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_ASSIST_LONG_PRESS_ACTION,
                    KEY_ASSIST_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_APP_SWITCH_ACTION,
                    KEY_APP_SWITCH_ACTION_VALIDATOR);
            VALIDATORS.put(KEY_APP_SWITCH_LONG_PRESS_ACTION,
                    KEY_APP_SWITCH_LONG_PRESS_ACTION_VALIDATOR);
            VALIDATORS.put(VOLBTN_MUSIC_CONTROLS,
                    VOLBTN_MUSIC_CONTROLS_VALIDATOR);
            VALIDATORS.put(HOME_WAKE_SCREEN, HOME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(BACK_WAKE_SCREEN, BACK_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(MENU_WAKE_SCREEN, MENU_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(ASSIST_WAKE_SCREEN, ASSIST_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(APP_SWITCH_WAKE_SCREEN,
                    APP_SWITCH_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(CAMERA_WAKE_SCREEN, CAMERA_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(VOLUME_WAKE_SCREEN, VOLUME_WAKE_SCREEN_VALIDATOR);
            VALIDATORS.put(ENABLE_FORWARD_LOOKUP,
                    ENABLE_FORWARD_LOOKUP_VALIDATOR);
            VALIDATORS.put(ENABLE_PEOPLE_LOOKUP,
                    ENABLE_PEOPLE_LOOKUP_VALIDATOR);
            VALIDATORS.put(ENABLE_REVERSE_LOOKUP,
                    ENABLE_REVERSE_LOOKUP_VALIDATOR);
            VALIDATORS.put(FORWARD_LOOKUP_PROVIDER,
                    FORWARD_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(PEOPLE_LOOKUP_PROVIDER,
                    PEOPLE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(REVERSE_LOOKUP_PROVIDER,
                    REVERSE_LOOKUP_PROVIDER_VALIDATOR);
            VALIDATORS.put(DIALER_OPENCNAM_ACCOUNT_SID,
                    DIALER_OPENCNAM_ACCOUNT_SID_VALIDATOR);
            VALIDATORS.put(DIALER_OPENCNAM_AUTH_TOKEN,
                    DIALER_OPENCNAM_AUTH_TOKEN_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_BATTERY_STYLE,
                    STATUS_BAR_BATTERY_STYLE_VALIDATOR);
            VALIDATORS.put(BERRY_GLOBAL_STYLE, BERRY_GLOBAL_STYLE_VALIDATOR);
            VALIDATORS.put(BERRY_CURRENT_ACCENT, BERRY_CURRENT_ACCENT_VALIDATOR);
            VALIDATORS.put(BERRY_DARK_OVERLAY, BERRY_DARK_OVERLAY_VALIDATOR);
            VALIDATORS.put(BERRY_MANAGED_BY_APP, BERRY_MANAGED_BY_APP_VALIDATOR);
            VALIDATORS.put(BERRY_BLACK_THEME, BERRY_BLACK_THEME_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_AM_PM, STATUS_BAR_AM_PM_VALIDATOR);
            VALIDATORS.put(STATUS_BAR_QUICK_QS_PULLDOWN,
                    STATUS_BAR_QUICK_QS_PULLDOWN_VALIDATOR);
            VALIDATORS.put(HIGH_TOUCH_SENSITIVITY_ENABLE,
                    HIGH_TOUCH_SENSITIVITY_ENABLE_VALIDATOR);
            VALIDATORS.put(SWIPE_TO_SCREENSHOT, SWIPE_TO_SCREENSHOT_VALIDATOR);
            VALIDATORS.put(BUTTON_BRIGHTNESS, BUTTON_BRIGHTNESS_VALIDATOR);
            VALIDATORS.put(BUTTON_BACKLIGHT_TIMEOUT, BUTTON_BACKLIGHT_TIMEOUT_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                    NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_SCREEN_ON,
                    NOTIFICATION_LIGHT_SCREEN_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_DEFAULT_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_COLOR,
                    NOTIFICATION_LIGHT_PULSE_CALL_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_CALL_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_CALL_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_COLOR_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_LED_ON_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF,
                    NOTIFICATION_LIGHT_PULSE_VMAIL_LED_OFF_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE,
                    NOTIFICATION_LIGHT_PULSE_CUSTOM_ENABLE_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES,
                    NOTIFICATION_LIGHT_PULSE_CUSTOM_VALUES_VALIDATOR);
            VALIDATORS.put(NOTIFICATION_LIGHT_COLOR_AUTO,
                    NOTIFICATION_LIGHT_COLOR_AUTO_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_ENABLED, BATTERY_LIGHT_ENABLED_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_PULSE, BATTERY_LIGHT_PULSE_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_LOW_COLOR, BATTERY_LIGHT_LOW_COLOR_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_MEDIUM_COLOR, BATTERY_LIGHT_MEDIUM_COLOR_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_FULL_COLOR, BATTERY_LIGHT_FULL_COLOR_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_BRIGHTNESS_LEVEL,
                    BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(BATTERY_LIGHT_BRIGHTNESS_LEVEL_ZEN,
                    BATTERY_LIGHT_BRIGHTNESS_LEVEL_VALIDATOR);
            VALIDATORS.put(ZEN_ALLOW_LIGHTS, ZEN_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put(ZEN_PRIORITY_ALLOW_LIGHTS, ZEN_PRIORITY_ALLOW_LIGHTS_VALIDATOR);
            VALIDATORS.put(ALERT_SLIDER_ORDER, ALERT_SLIDER_ORDER_VALIDATOR);
            VALIDATORS.put(ALERT_SLIDER_SILENT_MODE, ALERT_SLIDER_SILENT_MODE_VALIDATOR);
            VALIDATORS.put(CAMERA_SLEEP_ON_RELEASE, CAMERA_SLEEP_ON_RELEASE_VALIDATOR);
            VALIDATORS.put(CAMERA_LAUNCH, CAMERA_LAUNCH_VALIDATOR);
            VALIDATORS.put(__MAGICAL_TEST_PASSING_ENABLER,
                    __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR);
        };
        // endregion
    }

    /**
     * Secure settings, containing miscellaneous Evervolv secure preferences. This
     * table holds simple name/value pairs. There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class Secure extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/secure");

        public static final String SYS_PROP_SETTING_VERSION = "sys.evervolv_settings_secure_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_SECURE,
                CALL_METHOD_PUT_SECURE);

        /** @hide */
        protected static final ArraySet<String> MOVED_TO_GLOBAL;
        static {
            MOVED_TO_GLOBAL = new ArraySet<>(1);
        }

        // region Methods

        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from EVSettings.Secure"
                        + " to EVSettings.Global, value is unchanged.");
                return EVSettings.Global.getStringForUser(resolver, name, userId);
            }
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
               int userId) {
            if (MOVED_TO_GLOBAL.contains(name)) {
                Log.w(TAG, "Setting " + name + " has moved from EVSettings.Secure"
                        + " to EVSettings.Global, value is unchanged.");
                return false;
            }
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new EVSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // Secure Settings start

        /**
        * Developer options - Navigation Bar show switch
        * @hide
        */
        public static final String DEV_FORCE_SHOW_NAVBAR = "dev_force_show_navbar";

        /* @hide */
        public static final Validator DEV_FORCE_SHOW_VALIDATOR = sBooleanValidator;

        /**
         * String to contain power menu actions
         * @hide
         */
        public static final String POWER_MENU_ACTIONS = "power_menu_actions";

        private static final Validator POWER_MENU_ACTIONS_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * Whether to include options in power menu for rebooting into recovery or bootloader
         * @hide
         */
        public static final String ADVANCED_REBOOT = "advanced_reboot";

        /* @hide */
        public static final Validator ADVANCED_REBOOT_VALIDATOR = sBooleanValidator;

        /**
         * Whether touch hovering is enabled on supported hardware
         * @hide
         */
        public static final String FEATURE_TOUCH_HOVERING = "feature_touch_hovering";

        /* @hide */
        public static final Validator FEATURE_TOUCH_HOVERING_VALIDATOR = sBooleanValidator;

        /**
         * Vibrator intensity setting for supported devices
         * @hide
         */
        public static final String VIBRATOR_INTENSITY = "vibrator_intensity";

        /* @hide */
        public static final Validator VIBRATOR_INTENSITY_VALIDATOR =
                sNonNullStringValidator;

        /**
         * Performance profile
         * @hide
         */
        public static final String PERFORMANCE_PROFILE = "performance_profile";

        /**
         * App-based performance profile selection
         * @hide
         */
        public static final String APP_PERFORMANCE_PROFILES_ENABLED = "app_perf_profiles_enabled";

        /**
         * Whether volume panel should appear on the left (or right).
         * 0 = false (on the right)
         * 1 = true (on the left)
         */
        public static final String VOLUME_PANEL_ON_LEFT = "volume_panel_on_left";

        public static final Validator VOLUME_PANEL_ON_LEFT_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether volume panel is expandable.
         * 0 = false (AOSP Style)
         * 1 = true (Expandable)
         */
        public static final String VOLUME_PANEL_EXPANDABLE = "volume_panel_expandable";

        public static final Validator VOLUME_PANEL_EXPANDABLE_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to show the keyguard visualizer.
         * Boolean setting. 0 = off, 1 = on.
         * @hide
         */
        public static final String LOCKSCREEN_VISUALIZER_ENABLED = "lockscreen_visualizer";

        /* @hide */
        public static final Validator LOCKSCREEN_VISUALIZER_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to show media art on lockscreen
         * Boolean setting. 0 = off, 1 = on.
         * @hide
         */
        public static final String LOCKSCREEN_MEDIA_METADATA = "lockscreen_media_metadata";

        /* @hide */
        public static final Validator LOCKSCREEN_MEDIA_METADATA_VALIDATOR =
                sBooleanValidator;

        /**
         * Whether to have translucent background on lockscreen notifications
         * @hide
         */
        public static final String LOCKSCREEN_TRANSLUCENT_NOTIFICATIONS_BG_ENABLED
                = "lockscreen_translucent_notifications_bg_enabled";

        /* @hide */
        public static final Validator LOCKSCREEN_TRANSLUCENT_NOTIFICATIONS_BG_ENABLED_VALIDATOR =
                sBooleanValidator;

        /**
         * Network traffic indicator mode
         * 0 = Don't show network traffic indicator
         * 1 = Display up-stream traffic only
         * 2 = Display down-stream traffic only
         * 3 = Display both up- and down-stream traffic
         * @hide
         */
        public static final String NETWORK_TRAFFIC_MODE = "network_traffic_mode";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_MODE_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Whether or not to hide the network traffic indicator when there is no activity
         * @hide
         */
        public static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_AUTOHIDE_VALIDATOR = sBooleanValidator;

        /**
         * Measurement unit preference for network traffic
         * @hide
         */
        public static final String NETWORK_TRAFFIC_UNITS = "network_traffic_units";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_UNITS_VALIDATOR =
                new InclusiveIntegerRangeValidator(0, 3);

        /**
         * Whether or not to show measurement units in the network traffic indiciator
         * @hide
         */
        public static final String NETWORK_TRAFFIC_SHOW_UNITS = "network_traffic_show_units";

        /** @hide */
        public static final Validator NETWORK_TRAFFIC_SHOW_UNITS_VALIDATOR = sBooleanValidator;

        // Secure Settings end

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * Don't
         * @hide
         * me bro
         */
        public static final Validator __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR =
                sAlwaysTrueValidator;

        /**
         * @hide
         */
        public static final String[] LEGACY_SECURE_SETTINGS = new String[] {
            // Insert legacy secure settings here
            EVSettings.Secure.DEV_FORCE_SHOW_NAVBAR,
            EVSettings.Secure.POWER_MENU_ACTIONS,
            EVSettings.Secure.ADVANCED_REBOOT,
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_SECURE_SETTINGS, key);
        }

        /**
         * Mapping of validators for all secure settings.  This map is used to validate both valid
         * keys as well as validating the values for those keys.
         *
         * Note: Make sure if you add a new Secure setting you create a Validator for it and add
         *       it to this map.
         *
         * @hide
         */
        public static final Map<String, Validator> VALIDATORS =
                new ArrayMap<String, Validator>();
        static {
            VALIDATORS.put(DEV_FORCE_SHOW_NAVBAR,
                    DEV_FORCE_SHOW_VALIDATOR);
            VALIDATORS.put(ADVANCED_REBOOT,
                    ADVANCED_REBOOT_VALIDATOR);
            VALIDATORS.put(POWER_MENU_ACTIONS,
                    POWER_MENU_ACTIONS_VALIDATOR);
            VALIDATORS.put(FEATURE_TOUCH_HOVERING, FEATURE_TOUCH_HOVERING_VALIDATOR);
            VALIDATORS.put(VIBRATOR_INTENSITY, VIBRATOR_INTENSITY_VALIDATOR);
            VALIDATORS.put(VOLUME_PANEL_ON_LEFT, VOLUME_PANEL_ON_LEFT_VALIDATOR);
            VALIDATORS.put(VOLUME_PANEL_EXPANDABLE, VOLUME_PANEL_EXPANDABLE_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_VISUALIZER_ENABLED, LOCKSCREEN_VISUALIZER_ENABLED_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_TRANSLUCENT_NOTIFICATIONS_BG_ENABLED, LOCKSCREEN_TRANSLUCENT_NOTIFICATIONS_BG_ENABLED_VALIDATOR);
            VALIDATORS.put(LOCKSCREEN_MEDIA_METADATA, LOCKSCREEN_MEDIA_METADATA_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_MODE, NETWORK_TRAFFIC_MODE_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_AUTOHIDE, NETWORK_TRAFFIC_AUTOHIDE_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_UNITS, NETWORK_TRAFFIC_UNITS_VALIDATOR);
            VALIDATORS.put(NETWORK_TRAFFIC_SHOW_UNITS, NETWORK_TRAFFIC_SHOW_UNITS_VALIDATOR);
            VALIDATORS.put(__MAGICAL_TEST_PASSING_ENABLER,
                    __MAGICAL_TEST_PASSING_ENABLER_VALIDATOR);
        }
    }

    /**
     * Global settings, containing miscellaneous Evervolv global preferences. This
     * table holds simple name/value pairs. There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class Global extends Settings.NameValueTable {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/global");

        public static final String SYS_PROP_SETTING_VERSION = "sys.evervolv_settings_global_version";

        private static final NameValueCache sNameValueCache = new NameValueCache(
                SYS_PROP_SETTING_VERSION,
                CONTENT_URI,
                CALL_METHOD_GET_GLOBAL,
                CALL_METHOD_PUT_GLOBAL);

        // region Methods

        /**
         * Put a delimited list as a string
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split
         * @param list to join and store
         * @hide
         */
        public static void putListAsDelimitedString(ContentResolver resolver, String name,
                                                    String delimiter, List<String> list) {
            String store = TextUtils.join(delimiter, list);
            putString(resolver, name, store);
        }

        /**
         * Get a delimited string returned as a list
         * @param resolver to access the database with
         * @param name to store
         * @param delimiter to split the list with
         * @return list of strings for a specific Settings.Secure item
         * @hide
         */
        public static List<String> getDelimitedStringAsList(ContentResolver resolver, String name,
                                                            String delimiter) {
            String baseString = getString(resolver, name);
            List<String> list = new ArrayList<String>();
            if (!TextUtils.isEmpty(baseString)) {
                final String[] array = TextUtils.split(baseString, Pattern.quote(delimiter));
                for (String item : array) {
                    if (TextUtils.isEmpty(item)) {
                        continue;
                    }
                    list.add(item);
                }
            }
            return list;
        }

        /**
         * Construct the content URI for a particular name/value pair, useful for monitoring changes
         * with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI
         */
        public static Uri getUriFor(String name) {
            return Settings.NameValueTable.getUriFor(CONTENT_URI, name);
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            return getStringForUser(resolver, name, UserHandle.myUserId());
        }

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @param def Value to return if the setting is not defined.
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name, String def) {
            String str = getStringForUser(resolver, name, UserHandle.myUserId());
            return str == null ? def : str;
        }

        /** @hide */
        public static String getStringForUser(ContentResolver resolver, String name,
                int userId) {
            return sNameValueCache.getStringForUser(resolver, name, userId);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver, String name, String value) {
            return putStringForUser(resolver, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putStringForUser(ContentResolver resolver, String name, String value,
                int userId) {
            return sNameValueCache.putStringForUser(resolver, name, value, userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            return getIntForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int def, int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getIntForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static int getIntForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putIntForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putIntForUser(ContentResolver cr, String name, int value,
                int userId) {
            return putStringForUser(cr, name, Integer.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.  The default value will be returned if the setting is
         * not defined or not a {@code long}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid {@code long}.
         */
        public static long getLong(ContentResolver cr, String name, long def) {
            return getLongForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, long def,
                int userId) {
            String valString = getStringForUser(cr, name, userId);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : def;
            } catch (NumberFormatException e) {
                value = def;
            }
            return value;
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a {@code long}.  Note that internally setting values are always
         * stored as strings; this function converts the string to a {@code long}
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @return The setting's current value.
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         */
        public static long getLong(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getLongForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static long getLongForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String valString = getStringForUser(cr, name, userId);
            try {
                return Long.parseLong(valString);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a long
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putLong(ContentResolver cr, String name, long value) {
            return putLongForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putLongForUser(ContentResolver cr, String name, long value,
                int userId) {
            return putStringForUser(cr, name, Long.toString(value), userId);
        }

        /**
         * Convenience function for retrieving a single settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            return getFloatForUser(cr, name, def, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, float def,
                int userId) {
            String v = getStringForUser(cr, name, userId);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link EVSettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws EVSettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws EVSettingNotFoundException {
            return getFloatForUser(cr, name, UserHandle.myUserId());
        }

        /** @hide */
        public static float getFloatForUser(ContentResolver cr, String name, int userId)
                throws EVSettingNotFoundException {
            String v = getStringForUser(cr, name, userId);
            if (v == null) {
                throw new EVSettingNotFoundException(name);
            }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new EVSettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putFloatForUser(cr, name, value, UserHandle.myUserId());
        }

        /** @hide */
        public static boolean putFloatForUser(ContentResolver cr, String name, float value,
                int userId) {
            return putStringForUser(cr, name, Float.toString(value), userId);
        }

        // endregion

        // Global Settings start

        // Global Settings end

        /**
         * I can haz more bukkits
         * @hide
         */
        public static final String __MAGICAL_TEST_PASSING_ENABLER =
                "___magical_test_passing_enabler";

        /**
         * @hide
         */
        public static final String[] LEGACY_GLOBAL_SETTINGS = new String[] {
            // Insert legacy global settings here
        };

        /**
         * @hide
         */
        public static boolean isLegacySetting(String key) {
            return ArrayUtils.contains(LEGACY_GLOBAL_SETTINGS, key);
        }
    }
}
