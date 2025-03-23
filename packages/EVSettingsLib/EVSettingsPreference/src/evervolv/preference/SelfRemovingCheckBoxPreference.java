/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2018 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package evervolv.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceViewHolder;

/**
 * A SwitchPreference which can automatically remove itself from the hierarchy
 * based on constraints set in XML.
 */
public abstract class SelfRemovingCheckBoxPreference extends CheckBoxPreference {

    private final ConstraintsHelper mConstraints;

    public SelfRemovingCheckBoxPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingCheckBoxPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConstraints = new ConstraintsHelper(context, attrs, this);
    }

    public SelfRemovingCheckBoxPreference(Context context) {
        super(context);
        mConstraints = new ConstraintsHelper(context, null, this);
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
    protected abstract void putBoolean(String key, boolean value);
    protected abstract boolean getBoolean(String key, boolean defaultValue);

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        final boolean checked;
        if (!restorePersistedValue || !isPersisted()) {
            if (defaultValue == null) {
                return;
            }
            checked = (boolean) defaultValue;
            if (shouldPersist()) {
                persistBoolean(checked);
            }
        } else {
            // Note: the default is not used because to have got here
            // isPersisted() must be true.
            checked = getBoolean(getKey(), false /* not used */);
        }
        setChecked(checked);
    }

    private class DataStore extends PreferenceDataStore {
        @Override
        public void putBoolean(String key, boolean value) {
            SelfRemovingCheckBoxPreference.this.putBoolean(key, value);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return SelfRemovingCheckBoxPreference.this.getBoolean(key, defaultValue);
        }
    }
}
