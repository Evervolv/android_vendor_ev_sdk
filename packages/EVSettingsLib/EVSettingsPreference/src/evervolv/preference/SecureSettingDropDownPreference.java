/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod project
 * SPDX-License-Identifier: Apache-2.0
 */
package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

public class SecureSettingDropDownPreference extends SelfRemovingDropDownPreference {
    public SecureSettingDropDownPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SecureSettingDropDownPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getIntValue(int defValue) {
        return getValue() == null ? defValue : Integer.valueOf(getValue());
    }

    @Override
    protected boolean isPersisted() {
        return Settings.Secure.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected void putString(String key, String value) {
        Settings.Secure.putString(getContext().getContentResolver(), key, value);
    }

    @Override
    protected String getString(String key, String defaultValue) {
        return Settings.Secure.getString(getContext().getContentResolver(), key);
    }
}
