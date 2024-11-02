/*
 * SPDX-FileCopyrightText: 2014 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.preference;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

public class GlobalSettingSwitchPreference extends SelfRemovingSwitchPreference {

    public GlobalSettingSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public GlobalSettingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GlobalSettingSwitchPreference(Context context) {
        super(context);
    }

    @Override
    protected boolean isPersisted() {
        return Settings.Global.getString(getContext().getContentResolver(), getKey()) != null;
    }

    @Override
    protected void putBoolean(String key, boolean value) {
        Settings.Global.putInt(getContext().getContentResolver(), getKey(), value ? 1 : 0);
    }

    @Override
    protected boolean getBoolean(String key, boolean defaultValue) {
        return Settings.Global.getInt(getContext().getContentResolver(),
                getKey(), defaultValue ? 1 : 0) != 0;
    }
}
