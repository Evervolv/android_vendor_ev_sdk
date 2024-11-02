/*
 * SPDX-FileCopyrightText: 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.settingslib.widget;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;

import com.android.settingslib.widget.MainSwitchPreference;

public class SecureSettingMainSwitchPreference extends MainSwitchPreference {

    public SecureSettingMainSwitchPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new DataStore());
    }

    public SecureSettingMainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new DataStore());
    }

    public SecureSettingMainSwitchPreference(Context context) {
        super(context, null);
        setPreferenceDataStore(new DataStore());
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            Settings.Secure.putInt(getContext().getContentResolver(), key, value ? 1 : 0);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return Settings.Secure.getInt(getContext().getContentResolver(), key,
                    defaultValue ? 1 : 0) != 0;
        }
    }
}
