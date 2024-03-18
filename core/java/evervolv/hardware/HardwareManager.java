/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
 *               2017-2021 The LineageOS Project
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

import com.android.internal.annotations.VisibleForTesting;

import evervolv.app.ContextConstants;
import evervolv.hardware.TouchscreenGesture;

import java.lang.reflect.Field;

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
     * Hardware navigation key disablement
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_DISABLE = 0x1;

    /**
     * Touchscreen gesture
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCHSCREEN_GESTURES = 0x2;

    /**
     * High touch sensitivity for touch panels
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x4;

    /**
     * Touchscreen hovering
     */
    @VisibleForTesting
    public static final int FEATURE_TOUCH_HOVERING = 0x8;

    /**
     * Hardware key swap
     */
    @VisibleForTesting
    public static final int FEATURE_KEY_SWAP = 0x10;

    /**
     * High touch polling rate
     */
    @VisibleForTesting
    public static final int FEATURE_HIGH_TOUCH_POLLING_RATE = 0x20;

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    private HardwareManager(Context context) {
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
     * Determine if a Lineage Hardware feature is supported on this device
     *
     * @param feature The Lineage Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        try {
            if (checkService()) {
                return feature == (sService.getSupportedFeatures() & feature);
            }
        } catch (RemoteException e) {
        }
        return false;
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
    public boolean getFeature(int feature) {
        try {
            if (checkService()) {
                return sService.getFeature(feature);
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
    public boolean setFeature(int feature, boolean enable) {
        try {
            if (checkService()) {
                return sService.setFeature(feature, enable);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return a list of available touchscreen gestures on the devices
     */
    public TouchscreenGesture[] getGestures() {
        try {
            if (checkService()) {
                return sService.getGestures();
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * {@hide}
     */
    public TouchscreenGesture[] getTouchscreenGestures() {
        return getGestures();
    }

    /**
     * @return true if setting the activation status was successful
     */
    public boolean setGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        try {
            if (checkService()) {
                return sService.setGesture(gesture, state);
            }
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * {@hide}
     */
    public boolean setTouchscreenGestureEnabled(
            TouchscreenGesture gesture, boolean state) {
        return setGestureEnabled(gesture, state);
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
