/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2018 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import evervolv.provider.EVSettings;

public class EVGlobalSettingListPreference extends SelfRemovingListPreference {

    public EVGlobalSettingListPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EVGlobalSettingListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getIntValue(int defValue) {
        return getValue() == null ? defValue : Integer.valueOf(getValue());
    }

    @Override
    protected boolean isPersisted() {
        return EVSettings.Global.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected void putString(String key, String value) {
        EVSettings.Global.putString(getContext().getContentResolver(), key, value);
    }

    @Override
    protected String getString(String key, String defaultValue) {
        return EVSettings.Global.getString(getContext().getContentResolver(), key);
    }
}
