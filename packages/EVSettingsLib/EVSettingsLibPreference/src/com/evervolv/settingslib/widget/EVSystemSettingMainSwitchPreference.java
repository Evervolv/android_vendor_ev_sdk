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

public class EVSystemSettingMainSwitchPreference extends MainSwitchPreference {

    public EVSystemSettingMainSwitchPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        setPreferenceDataStore(new DataStore());
    }

    public EVSystemSettingMainSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPreferenceDataStore(new DataStore());
    }

    public EVSystemSettingMainSwitchPreference(Context context) {
        super(context);
        setPreferenceDataStore(new DataStore());
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            EVSettings.System.putInt(getContext().getContentResolver(), key, value ? 1 : 0);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return EVSettings.System.getInt(getContext().getContentResolver(), key,
                    defaultValue ? 1 : 0) != 0;
        }
    }
}
