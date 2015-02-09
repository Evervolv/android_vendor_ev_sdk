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
package evervolv.hardware;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Range;

import com.android.internal.annotations.VisibleForTesting;

import evervolv.app.ContextConstants;

import java.io.UnsupportedEncodingException;
import java.lang.IllegalArgumentException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Manages access to hardware extensions
 *
 *  <p>
 *  This manager requires the HARDWARE_ABSTRACTION_ACCESS permission.
 *  <p>
 *  To get the instance of this class, utilize HardwareManager#getInstance(Context context)
 */
public final class HardwareManager {
    private static final String TAG = "HardwareManager";

    private static IHardwareService sService;
    private static HardwareManager sHardwareManagerInstance;

    private Context mContext;

    /**
     * Variable vibrator intensity
     */
    @VisibleForTesting
    public static final int FEATURE_VIBRATOR = 0x1;

    /**
     * Hardware navigation key disablement
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_DISABLE = 0x2;

    /**
     * Touchscreen gesture
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCHSCREEN_GESTURES = 0x4;

    /**
     * High touch sensitivity for touch panels
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x8;

    /**
     * Touchscreen hovering
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCH_HOVERING = 0x10;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_KEY_DISABLE,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_TOUCH_HOVERING
    );

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private HardwareManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext != null) {
            mContext = appContext;
        } else {
            mContext = context;
        }
        sService = getService();

        if (context.getPackageManager().hasSystemFeature(
                ContextConstants.Features.HARDWARE_ABSTRACTION) && !checkService()) {
            Log.wtf(TAG, "Unable to get HardwareService. The service either" +
                    " crashed, was not started, or the interface has been called to early in" +
                    " SystemServer init");
        }
    }

    /**
     * Get or create an instance of the {@link evervolv.hardware.HardwareManager}
     * @param context
     * @return {@link HardwareManager}
     */
    public static HardwareManager getInstance(Context context) {
        if (sHardwareManagerInstance == null) {
            sHardwareManagerInstance = new HardwareManager(context);
        }
        return sHardwareManagerInstance;
    }

    /** @hide */
    public static IHardwareService getService() {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(ContextConstants.HARDWARE_MANAGER);
        if (b != null) {
            sService = IHardwareService.Stub.asInterface(b);
            return sService;
        }
        return null;
    }

    /**
     * @return the supported features bitmask
     */
    public int getSupportedFeatures() {
        try {
            if (checkService()) {
                return sService.getSupportedFeatures();
            }
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Determine if a Device Hardware feature is supported on this device
     *
     * @param feature The Device Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        return feature == (getSupportedFeatures() & feature);
    }

    /**
     * String version for preference constraints
     *
     * @hide
     */
    public boolean isSupported(String feature) {
        if (!feature.startsWith("FEATURE_")) {
            return false;
        }
        try {
            Field f = getClass().getField(feature);
            if (f != null) {
                return isSupported((int) f.get(null));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.d(TAG, e.getMessage(), e);
        }

        return false;
    }
    /**
     * Determine if the given feature is enabled or disabled.
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Device Hardware feature to query
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean get(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (checkService()) {
                return sService.get(feature);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Enable or disable the given feature
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the Hardware feature to set
     * @param enable true to enable, false to disale
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean set(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        try {
            if (checkService()) {
                return sService.set(feature, enable);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    private int getArrayValue(int[] arr, int idx, int defaultValue) {
        if (arr == null || arr.length <= idx) {
            return defaultValue;
        }

        return arr[idx];
    }

    /**
     * {@hide}
     */
    public static final int VIBRATOR_INTENSITY_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_DEFAULT_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MIN_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MAX_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_WARNING_INDEX = 4;

    private int[] getVibratorIntensityArray() {
        try {
            if (checkService()) {
                return sService.getVibratorIntensity();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return The current vibrator intensity.
     */
    public int getVibratorIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_INTENSITY_INDEX, 0);
    }

    /**
     * @return The default vibrator intensity.
     */
    public int getVibratorDefaultIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_DEFAULT_INDEX, 0);
    }

    /**
     * @return The minimum vibrator intensity.
     */
    public int getVibratorMinIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MIN_INDEX, 0);
    }

    /**
     * @return The maximum vibrator intensity.
     */
    public int getVibratorMaxIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MAX_INDEX, 0);
    }

    /**
     * @return The warning threshold vibrator intensity.
     */
    public int getVibratorWarningIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_WARNING_INDEX, 0);
    }

    /**
     * Set the current vibrator intensity
     *
     * @param intensity the intensity to set, between {@link #getVibratorMinIntensity()} and
     * {@link #getVibratorMaxIntensity()} inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setVibratorIntensity(int intensity) {
        try {
            if (checkService()) {
                return sService.setVibratorIntensity(intensity);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return a list of available touchscreen gestures on the devices
     */
    public TouchscreenGesture[] getTouchscreenGestures() {
        try {
            if (checkService()) {
                return sService.getTouchscreenGestures();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if setting the activation status was successful
     */
    public boolean setTouchscreenGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        try {
            if (checkService()) {
                return sService.setTouchscreenGestureEnabled(gesture, state);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return true if service is valid
     */
    private boolean checkService() {
        if (sService == null) {
            Log.w(TAG, "not connected to HardwareManagerService");
            return false;
        }
        return true;
    }
}
