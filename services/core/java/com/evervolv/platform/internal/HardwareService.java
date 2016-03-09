/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2019 The LineageOS Project
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
import android.database.ContentObserver;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Uri;
import android.util.Log;

import com.android.server.display.color.DisplayTransformManager;
import com.android.server.LocalServices;

import evervolv.app.ContextConstants;
import evervolv.hardware.HardwareManager;
import evervolv.hardware.IHardwareService;
import evervolv.hardware.TouchscreenGesture;
import evervolv.provider.EVSettings;

import java.lang.IllegalArgumentException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import vendor.evervolv.touch.V1_0.Gesture;
import vendor.evervolv.touch.V1_0.IHighTouchPollingRate;
import vendor.evervolv.touch.V1_0.IGloveMode;
import vendor.evervolv.touch.V1_0.IKeyDisabler;
import vendor.evervolv.touch.V1_0.IKeySwapper;
import vendor.evervolv.touch.V1_0.IStylusMode;
import vendor.evervolv.touch.V1_0.ITouchscreenGesture;

import static evervolv.hardware.HardwareManager.FEATURE_KEY_DISABLE;
import static evervolv.hardware.HardwareManager.FEATURE_TOUCHSCREEN_GESTURES;
import static evervolv.hardware.HardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY;
import static evervolv.hardware.HardwareManager.FEATURE_TOUCH_HOVERING;
import static evervolv.hardware.HardwareManager.FEATURE_KEY_SWAP;
import static evervolv.hardware.HardwareManager.FEATURE_HIGH_TOUCH_POLLING_RATE;

/** @hide */
public class HardwareService extends VendorService {

    private static final boolean DEBUG = true;
    private static final String TAG = HardwareService.class.getSimpleName();

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_KEY_SWAP,
        FEATURE_TOUCH_HOVERING,
        FEATURE_HIGH_TOUCH_POLLING_RATE
    );

    private final Context mContext;

    private IHighTouchPollingRate mHighTouchPollingRate = null;
    private IGloveMode mGloveMode = null;
    private IKeyDisabler mKeyDisabler = null;
    private IKeySwapper mKeySwapper = null;
    private IStylusMode mStylusMode = null;
    private ITouchscreenGesture mTouchscreenGesture;
    private TouchscreenGesture[] mAvailableGestures;

    private int mSupportedFeatures = 0;

    private SettingsObserver mSettingsObserver;

    public HardwareService(Context context) {
        super(context);
        mContext = context;

        try {
            mHighTouchPollingRate = IHighTouchPollingRate.getService();
            mSupportedFeatures |= FEATURE_HIGH_TOUCH_POLLING_RATE;
        } catch (NoSuchElementException | RemoteException e) { }

        try {
            mGloveMode = IGloveMode.getService();
            mSupportedFeatures |= FEATURE_HIGH_TOUCH_SENSITIVITY;
        } catch (NoSuchElementException | RemoteException e) { }

        try {
            mKeyDisabler = IKeyDisabler.getService();
            mSupportedFeatures |= FEATURE_KEY_DISABLE;
        } catch (NoSuchElementException | RemoteException e) { }

        try {
            mKeySwapper = IKeySwapper.getService();
            mSupportedFeatures |= FEATURE_KEY_SWAP;
        } catch (NoSuchElementException | RemoteException e) { }

        try {
            mStylusMode = IStylusMode.getService();
            mSupportedFeatures |= FEATURE_TOUCH_HOVERING;
        } catch (NoSuchElementException | RemoteException e) { }

        try {
            mTouchscreenGesture = ITouchscreenGesture.getService();
            ArrayList<Gesture> availableGestures = mTouchscreenGesture.getSupportedGestures();
            int size = availableGestures.size();
            mAvailableGestures = new TouchscreenGesture[size];
            for (int i = 0; i < size; i++) {
                final Gesture g = availableGestures.get(i);
                mAvailableGestures[i] = new TouchscreenGesture(g.id, g.name, g.keycode);
            }
            mSupportedFeatures |= FEATURE_TOUCHSCREEN_GESTURES;
        } catch (NoSuchElementException | RemoteException e) { }

        mSettingsObserver = new SettingsObserver(new Handler());
        mSettingsObserver.observe();
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
        publishBinderService(ContextConstants.HARDWARE_MANAGER, mService);
    }

    private int getSupportedFeaturesInternal() {
        return mSupportedFeatures;
    }

    private boolean getFeatureInternal(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        switch (feature) {
            case FEATURE_HIGH_TOUCH_POLLING_RATE:
                try {
                    return mHighTouchPollingRate.isEnabled();
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_HIGH_TOUCH_SENSITIVITY:
                try {
                    return mGloveMode.isEnabled();
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_KEY_DISABLE:
                try {
                    return mKeyDisabler.isEnabled();
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_KEY_SWAP:
                try {
                    return mKeySwapper.isEnabled();
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_TOUCH_HOVERING:
                try {
                    return mStylusMode.isEnabled();
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            default:
                break;
        }
        return false;
    }

    private boolean setFeatureInternal(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }
        switch (feature) {
            case FEATURE_HIGH_TOUCH_POLLING_RATE:
                try {
                    return mHighTouchPollingRate.setEnabled(enable);
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_HIGH_TOUCH_SENSITIVITY:
                try {
                    return mGloveMode.setEnabled(enable);
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_KEY_DISABLE:
                try {
                    return mKeyDisabler.setEnabled(enable);
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_KEY_SWAP:
                try {
                    return mKeySwapper.setEnabled(enable);
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            case FEATURE_TOUCH_HOVERING:
                try {
                    return mStylusMode.setEnabled(enable);
                } catch (NoSuchElementException | RemoteException e) { }
                break;
            default:
                break;
        }
        return false;
    }

    private TouchscreenGesture[] getGesturesInternal() {
        return mAvailableGestures;
    }

    private boolean setGestureInternal(TouchscreenGesture gesture, boolean state) {
        try {
            Gesture compat = new Gesture();
            compat.id = gesture.id;
            compat.name = gesture.name;
            compat.keycode = gesture.keycode;
            return mTouchscreenGesture.setGestureEnabled(compat, state);
        } catch (RemoteException e) {
        }
        return false;
    }

    private final IBinder mService = new IHardwareService.Stub() {
        @Override
        public int getSupportedFeatures() {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return getSupportedFeaturesInternal();
        }

        @Override
        public boolean getFeature(int feature) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return getFeatureInternal(feature);
        }

        @Override
        public boolean setFeature(int feature, boolean enable) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return setFeatureInternal(feature, enable);
        }

        @Override
        public TouchscreenGesture[] getGestures() {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return getGesturesInternal();
        }

        @Override
        public boolean setGesture(TouchscreenGesture gesture, boolean state) {
            mContext.enforceCallingOrSelfPermission(
                    evervolv.platform.Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
            return setGestureInternal(gesture, state);
        }
    };

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            registerFeature(FEATURE_HIGH_TOUCH_POLLING_RATE,
                    EVSettings.System.getUriFor(
                            EVSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE));
            registerFeature(FEATURE_HIGH_TOUCH_SENSITIVITY,
                    EVSettings.System.getUriFor(
                            EVSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE));
            registerFeature(FEATURE_TOUCH_HOVERING,
                    EVSettings.Secure.getUriFor(
                            EVSettings.Secure.FEATURE_TOUCH_HOVERING));
            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        private void update() {
            final ContentResolver resolver = mContext.getContentResolver();

            final boolean highPollingRate = EVSettings.System.getInt(resolver,
                    EVSettings.System.HIGH_TOUCH_POLLING_RATE_ENABLE, 0) == 1;
            updateFeature(FEATURE_HIGH_TOUCH_POLLING_RATE, highPollingRate);

            final boolean gloveMode = EVSettings.System.getInt(resolver,
                    EVSettings.System.HIGH_TOUCH_SENSITIVITY_ENABLE, 0) == 1;
            updateFeature(FEATURE_HIGH_TOUCH_SENSITIVITY, gloveMode);

            final boolean stylusMode = EVSettings.Secure.getInt(resolver,
                    EVSettings.Secure.FEATURE_TOUCH_HOVERING, 0) == 1;
            updateFeature(FEATURE_TOUCH_HOVERING, stylusMode);
        }

        private void registerFeature(int feature, Uri uri) {
            if (feature != (mSupportedFeatures & feature))
                return;

            mContext.getContentResolver().registerContentObserver(
                    uri, false, this, UserHandle.USER_ALL);
        }

        private void updateFeature(int feature, boolean enabled) {
            if (feature != (mSupportedFeatures & feature))
                return;

            final boolean status = getFeatureInternal(feature);
            if (status != enabled) {
                setFeatureInternal(feature, enabled);
            }
        }
    }
}
