/*
 * SPDX-FileCopyrightText: 2013 The CyanogenMod project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

public class SecureCheckBoxPreference extends SelfRemovingCheckBoxPreference {
    public SecureCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SecureCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SecureCheckBoxPreference(Context context) {
        super(context, null);
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        Settings.Secure.putInt(getContext().getContentResolver(), getKey(), value ? 1 : 0);
    }

    @Override
    protected boolean isPersisted() {
        return Settings.Secure.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                getKey(), defaultValue ? 1 : 0) != 0;
    }
}
