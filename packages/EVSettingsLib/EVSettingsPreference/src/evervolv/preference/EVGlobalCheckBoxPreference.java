/*
 * SPDX-FileCopyrightText: 2013 The CyanogenMod project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import evervolv.provider.EVSettings;

public class EVGlobalCheckBoxPreference extends SelfRemovingCheckBoxPreference {
    public EVGlobalCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public EVGlobalCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EVGlobalCheckBoxPreference(Context context) {
        super(context, null);
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        EVSettings.Global.putInt(getContext().getContentResolver(), getKey(), value ? 1 : 0);
    }

    @Override
    protected boolean isPersisted() {
        return EVSettings.Global.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return EVSettings.Global.getInt(getContext().getContentResolver(),
                getKey(), defaultValue ? 1 : 0) != 0;
    }
}
