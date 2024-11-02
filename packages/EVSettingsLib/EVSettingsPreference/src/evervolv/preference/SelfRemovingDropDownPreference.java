/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2018 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package evervolv.preference;

import android.content.Context;
import androidx.preference.DropDownPreference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;

/**
 * A Preference which can automatically remove itself from the hierarchy
 * based on constraints set in XML.
 */
public abstract class SelfRemovingDropDownPreference extends DropDownPreference {

    private final ConstraintsHelper mConstraints;

    public SelfRemovingDropDownPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingDropDownPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingDropDownPreference(Context context) {
        super(context);
        mConstraints = new ConstraintsHelper(context, null, this);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        mConstraints.onAttached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        mConstraints.onBindViewHolder(holder);
    }

    public void setAvailable(boolean available) {
        mConstraints.setAvailable(available);
    }

    public boolean isAvailable() {
        return mConstraints.isAvailable();
    }

    protected abstract boolean isPersisted();
    protected abstract void putString(String key, String value);
    protected abstract String getString(String key, String defaultValue);

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        final String value;
        if (!restorePersistedValue || !isPersisted()) {
            if (defaultValue == null) {
                return;
            }
            value = (String) defaultValue;
            if (shouldPersist()) {
                persistString(value);
            }
        } else {
            // Note: the default is not used because to have got here
            // isPersisted() must be true.
            value = getString(getKey(), null /* not used */);
        }
        setValue(value);
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putString(String key, String value) {
            SelfRemovingDropDownPreference.this.putString(key, value);
        }

        @Override
        public String getString(String key, String defaultValue) {
            return SelfRemovingDropDownPreference.this.getString(key, defaultValue);
        }
    }
}
