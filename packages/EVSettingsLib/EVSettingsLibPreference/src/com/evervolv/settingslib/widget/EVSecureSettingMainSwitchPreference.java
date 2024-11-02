/*
 * SPDX-FileCopyrightText: 2021 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.settingslib.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceDataStore;

import com.android.settingslib.widget.MainSwitchPreference;

import evervolv.provider.EVSettings;

public class EVSecureSettingMainSwitchPreference extends MainSwitchPreference {

    public EVSecureSettingMainSwitchPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new DataStore());
    }

    public EVSecureSettingMainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new DataStore());
    }

    public EVSecureSettingMainSwitchPreference(Context context) {
        super(context);
        setPreferenceDataStore(new DataStore());
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            EVSettings.Secure.putInt(getContext().getContentResolver(), key, value ? 1 : 0);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return EVSettings.Secure.getInt(getContext().getContentResolver(), key,
                    defaultValue ? 1 : 0) != 0;
        }
    }
}
