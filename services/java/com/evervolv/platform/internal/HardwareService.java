/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evervolv.platform.internal;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Range;

import com.android.server.SystemService;

import evervolv.app.ContextConstants;
import evervolv.hardware.IHardwareService;
import evervolv.hardware.HardwareManager;
import evervolv.hardware.TouchscreenGesture;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.evervolv.hardware.KeyDisabler;
import com.evervolv.hardware.TouchscreenGestures;
import com.evervolv.hardware.VibratorHW;

/** @hide */
public class HardwareService extends VendorService {

    private static final boolean DEBUG = true;
    private static final String TAG = HardwareService.class.getSimpleName();

    private final Context mContext;
    private final HardwareInterface mHardwareImpl;

    private interface HardwareInterface {
        public int getSupportedFeatures();
        public boolean get(int feature);
        public boolean set(int feature, boolean enable);

        public int[] getVibratorIntensity();
        public boolean setVibratorIntensity(int intensity);

        public TouchscreenGesture[] getTouchscreenGestures();
        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state);
    }

    private class LegacyHardware implements HardwareInterface {

        private int mSupportedFeatures = 0;

        public LegacyHardware() {
            if (VibratorHW.isSupported())
                mSupportedFeatures |= HardwareManager.FEATURE_VIBRATOR;
            if (KeyDisabler.isSupported())
                mSupportedFeatures |= HardwareManager.FEATURE_KEY_DISABLE;
            if (TouchscreenGestures.isSupported())
                mSupportedFeatures |= HardwareManager.FEATURE_TOUCHSCREEN_GESTURES;
        }

        public int getSupportedFeatures() {
            return mSupportedFeatures;
        }

        public boolean get(int feature) {
            switch(feature) {
                case HardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.isActive();
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        public boolean set(int feature, boolean enable) {
            switch(feature) {
                case HardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.setActive(enable);
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        public int[] getVibratorIntensity() {
            int[] vibrator = new int[5];
            vibrator[HardwareManager.VIBRATOR_INTENSITY_INDEX] = VibratorHW.getCurIntensity();
            vibrator[HardwareManager.VIBRATOR_DEFAULT_INDEX] = VibratorHW.getDefaultIntensity();
            vibrator[HardwareManager.VIBRATOR_MIN_INDEX] = VibratorHW.getMinIntensity();
            vibrator[HardwareManager.VIBRATOR_MAX_INDEX] = VibratorHW.getMaxIntensity();
            vibrator[HardwareManager.VIBRATOR_WARNING_INDEX] = VibratorHW.getWarningThreshold();
            return vibrator;
        }

        public boolean setVibratorIntensity(int intensity) {
            return VibratorHW.setIntensity(intensity);
        }

        public TouchscreenGesture[] getTouchscreenGestures() {
            return TouchscreenGestures.getAvailableGestures();
        }

        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state) {
            return TouchscreenGestures.setGestureEnabled(gesture, state);
        }
    }

    private HardwareInterface getImpl(Context context) {
        return new LegacyHardware();
    }

    public HardwareService(Context context) {
        super(context);
        mContext = context;
        mHardwareImpl = getImpl(context);
        publishBinderService(ContextConstants.HARDWARE_MANAGER, mService);
    }

    @Override
    public String getFeatureDeclaration() {
        return ContextConstants.Features.HARDWARE_ABSTRACTION;
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            Intent intent = new Intent(evervolv.content.Intent.ACTION_INITIALIZE_HARDWARE);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS);
        }
    }

    @Override
    public void onStart() {
    }

    private final IBinder mService = new IHardwareService.Stub() {

        private boolean isSupported(int feature) {
            return (getSupportedFeatures() & feature) == feature;
        }

        @Override
        public int getSupportedFeatures() {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return mHardwareImpl.getSupportedFeatures();
        }

        @Override
        public boolean get(int feature) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(feature)) {
                Log.e(TAG, "feature " + feature + " is not supported");
                return false;
            }
            return mHardwareImpl.get(feature);
        }

        @Override
        public boolean set(int feature, boolean enable) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(feature)) {
                Log.e(TAG, "feature " + feature + " is not supported");
                return false;
            }
            return mHardwareImpl.set(feature, enable);
        }

        @Override
        public int[] getVibratorIntensity() {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(HardwareManager.FEATURE_VIBRATOR)) {
                Log.e(TAG, "Vibrator is not supported");
                return null;
            }
            return mHardwareImpl.getVibratorIntensity();
        }

        @Override
        public boolean setVibratorIntensity(int intensity) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(HardwareManager.FEATURE_VIBRATOR)) {
                Log.e(TAG, "Vibrator is not supported");
                return false;
            }
            return mHardwareImpl.setVibratorIntensity(intensity);
        }

        @Override
        public TouchscreenGesture[] getTouchscreenGestures() {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(HardwareManager.FEATURE_TOUCHSCREEN_GESTURES)) {
                Log.e(TAG, "Touchscreen gestures are not supported");
                return null;
            }
            return mHardwareImpl.getTouchscreenGestures();
        }

        @Override
        public boolean setTouchscreenGestureEnabled(TouchscreenGesture gesture, boolean state) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            if (!isSupported(HardwareManager.FEATURE_TOUCHSCREEN_GESTURES)) {
                Log.e(TAG, "Touchscreen gestures are not supported");
                return false;
            }
            return mHardwareImpl.setTouchscreenGestureEnabled(gesture, state);
        }
    };
}
