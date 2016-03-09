/*
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.internal.input;

import static evervolv.hardware.HardwareManager.FEATURE_HIGH_TOUCH_POLLING_RATE;
import static evervolv.hardware.HardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY;
import static evervolv.hardware.HardwareManager.FEATURE_TOUCH_HOVERING;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;

import evervolv.hardware.HardwareManager;
import evervolv.provider.EVSettings;

public final class TouchHardwareManager {
    private final String TAG = "TouchHardwareManager";

    private final Context mContext;
    private HardwareManager mHardwareManager;
    private SettingsObserver mSettingsObserver;

    public TouchHardwareManager(Context context) {
        mContext = context;

        mHardwareManager = HardwareManager.getInstance(mContext);

        mSettingsObserver = new SettingsObserver(new Handler());
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        public void onRegister() {
            ContentResolver resolver = mContext.getContentResolver();

            if (mHardwareManager.isSupported(FEATURE_HIGH_TOUCH_POLLING_RATE)) {
                resolver.registerContentObserver(EVSettings.System.getUriFor(
                        EVSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE),
                        false, this, UserHandle.USER_ALL);
            }

            if (mHardwareManager.isSupported(FEATURE_HIGH_TOUCH_SENSITIVITY)) {
                resolver.registerContentObserver(EVSettings.System.getUriFor(
                        EVSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE),
                        false, this, UserHandle.USER_ALL);
            }

            if (mHardwareManager.isSupported(FEATURE_TOUCH_HOVERING)) {
                resolver.registerContentObserver(EVSettings.Secure.getUriFor(
                        EVSettings.Secure.FEATURE_TOUCH_HOVERING),
                        false, this, UserHandle.USER_ALL);
            }

            updateAll();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateAll();
        }
    }

    public void onRegister() {
        mSettingsObserver.onRegister();
    }

    public void updateAll() {
        updateTouchHovering();
        updateTouchPollingRate();
        updateTouchSensitivity();
    }

    private void updateTouchPollingRate() {
        if (mHardwareManager.isSupported(FEATURE_HIGH_TOUCH_POLLING_RATE)) {
            return;
        }
        final boolean enabled = EVSettings.System.getInt(mContext.getContentResolver(),
                EVSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE, 0) == 1;
        mHardwareManager.set(FEATURE_HIGH_TOUCH_POLLING_RATE, enabled);
    }

    private void updateTouchSensitivity() {
        if (!mHardwareManager.isSupported(FEATURE_HIGH_TOUCH_SENSITIVITY)) {
            return;
        }
        final boolean enabled = EVSettings.System.getInt(mContext.getContentResolver(),
                EVSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, 0) == 1;
        mHardwareManager.set(FEATURE_HIGH_TOUCH_SENSITIVITY, enabled);
    }

    private void updateTouchHovering() {
        if (!mHardwareManager.isSupported(FEATURE_TOUCH_HOVERING)) {
            return;
        }
        final boolean enabled = EVSettings.Secure.getInt(mContext.getContentResolver(),
                EVSettings.Secure.FEATURE_TOUCH_HOVERING, 0) == 1;
        mHardwareManager.set(FEATURE_TOUCH_HOVERING, enabled);
    }
}
