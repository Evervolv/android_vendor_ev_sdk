/*
 * Copyright (C) 2016 The CyanogenMod Project
 *               2019 The LineageOS Project
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
package com.evervolv.platform.internal.display;

import static evervolv.hardware.LiveDisplayManager.MODE_AUTO;
import static evervolv.hardware.LiveDisplayManager.MODE_DAY;
import static evervolv.hardware.LiveDisplayManager.MODE_OUTDOOR;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.NoSuchElementException;

import evervolv.hardware.LiveDisplayManager;
import evervolv.provider.EVSettings;

import vendor.lineage.livedisplay.V2_0.ISunlightEnhancement;

public class OutdoorModeController extends LiveDisplayFeature {

    private static final String TAG = "OutdoorModeController";

    private ISunlightEnhancement mSunlightEnhancement = null;
    private AmbientLuxObserver mLuxObserver;

    // hardware capabilities
    private final boolean mUseOutdoorMode;

    // default values
    private final int mDefaultOutdoorLux;
    private final int mOutdoorLuxHysteresis;
    private final boolean mDefaultAutoOutdoorMode;

    // internal state
    private boolean mIsOutdoor;
    private boolean mIsSensorEnabled;

    // sliding window for sensor event smoothing
    private static final int SENSOR_WINDOW_MS = 3000;

    public OutdoorModeController(Context context, Handler handler) {
        super(context, handler);

        try {
            mSunlightEnhancement = ISunlightEnhancement.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseOutdoorMode = mSunlightEnhancement != null;

        mDefaultOutdoorLux = mContext.getResources().getInteger(
                com.evervolv.platform.internal.R.integer.config_outdoorAmbientLux);
        mOutdoorLuxHysteresis = mContext.getResources().getInteger(
                com.evervolv.platform.internal.R.integer.config_outdoorAmbientLuxHysteresis);
        mDefaultAutoOutdoorMode = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultAutoOutdoorMode);
    }

    @Override
    public void onStart() {
        if (!mUseOutdoorMode) {
            return;
        }

        mLuxObserver = new AmbientLuxObserver(mContext, mHandler.getLooper(),
                mDefaultOutdoorLux, mOutdoorLuxHysteresis, SENSOR_WINDOW_MS);

        registerSettings(
                EVSettings.System.getUriFor(EVSettings.System.DISPLAY_AUTO_OUTDOOR_MODE));
    }

    @Override
    public boolean getCapabilities(final BitSet caps) {
        if (mUseOutdoorMode) {
            caps.set(LiveDisplayManager.MODE_AUTO);
            caps.set(LiveDisplayManager.MODE_OUTDOOR);
        }
        return mUseOutdoorMode;
    }

    @Override
    protected void onUpdate() {
        updateOutdoorMode();
    }

    @Override
    protected void onTwilightUpdated() {
        updateOutdoorMode();
    }

    @Override
    protected synchronized void onScreenStateChanged() {
        if (!mUseOutdoorMode) {
            return;
        }

        // toggle the sensor when screen on/off
        updateSensorState();

        // Disable outdoor mode on screen off so that we don't melt the users
        // face if they turn it back on in normal conditions
        if (!isScreenOn() && getMode() != MODE_OUTDOOR) {
            mIsOutdoor = false;
            try {
                mSunlightEnhancement.setEnabled(false);
            } catch (NoSuchElementException | RemoteException e) {
            }
        }
    }

    @Override
    public synchronized void onSettingsChanged(Uri uri) {
        updateOutdoorMode();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("OutdoorModeController Configuration:");
        pw.println("  mDefaultOutdoorLux=" + mDefaultOutdoorLux);
        pw.println("  mOutdoorLuxHysteresis=" + mOutdoorLuxHysteresis);
        pw.println();
        pw.println("  OutdoorModeController State:");
        pw.println("    mAutoOutdoorMode=" + isAutomaticOutdoorModeEnabled());
        pw.println("    mIsOutdoor=" + mIsOutdoor);
        pw.println("    mIsNight=" + isNight());
        try {
            pw.println("    hardware state=" + mSunlightEnhancement.isEnabled());
        } catch (NoSuchElementException | RemoteException e) {
        }
        mLuxObserver.dump(pw);
    }

    private synchronized void updateSensorState() {
        if (!mUseOutdoorMode || mLuxObserver == null) {
            return;
        }

        /*
         * Light sensor:
         */
        boolean sensorEnabled = false;
        // no sensor if low power mode or when the screen is off
        if (isScreenOn() && !isLowPowerMode()) {
            if (isAutomaticOutdoorModeEnabled()) {
                int mode = getMode();
                if (mode == MODE_DAY) {
                    // always turn it on if day mode is selected
                    sensorEnabled = true;
                } else if (mode == MODE_AUTO && !isNight()) {
                    // in auto mode we turn it on during actual daytime
                    sensorEnabled = true;
                }
            }
        }
        if (mIsSensorEnabled != sensorEnabled) {
            mIsSensorEnabled = sensorEnabled;
            mLuxObserver.setTransitionListener(sensorEnabled ? mListener : null);
        }
    }

    /**
     * Outdoor mode is optionally enabled when ambient lux > 10000 and it's daytime
     * Melt faces!
     *
     * TODO: Use the camera or RGB sensor to determine if it's really sunlight
     */
    private synchronized void updateOutdoorMode() {
        if (!mUseOutdoorMode) {
            return;
        }

        updateSensorState();

        /*
         * Should we turn on outdoor mode or not?
         *
         * Do nothing if the screen is off.
         */
        if (isScreenOn()) {
            boolean enabled = false;
            // turn it off in low power mode
            if (!isLowPowerMode()) {
                int mode = getMode();
                // turn it on if the user manually selected the mode
                if (mode == MODE_OUTDOOR) {
                    enabled = true;
                } else if (isAutomaticOutdoorModeEnabled()) {
                    // self-managed mode means we just flip a switch and an external
                    // implementation does all the sensing. this allows the user
                    // to turn on/off the feature.
                    if (mIsOutdoor) {
                        // if we're here, the sensor detects extremely bright light.
                        if (mode == MODE_DAY) {
                            // if the user manually selected day mode, go ahead and
                            // melt their face
                            enabled = true;
                        } else if (mode == MODE_AUTO && !isNight()) {
                            // if we're in auto mode, we should also check if it's
                            // night time, since we don't get much sun at night
                            // on this planet :)
                            enabled = true;
                        }
                    }
                }
            }
            try {
                mSunlightEnhancement.setEnabled(enabled);
            } catch (NoSuchElementException | RemoteException e) {
            }
        }
    }

    private final AmbientLuxObserver.TransitionListener mListener =
            new AmbientLuxObserver.TransitionListener() {
        @Override
        public void onTransition(final int state, float ambientLux) {
            final boolean outdoor = state == 1;
            synchronized (OutdoorModeController.this) {
                if (mIsOutdoor == outdoor) {
                    return;
                }

                mIsOutdoor = outdoor;
                updateOutdoorMode();
            }
        }
    };

    boolean setAutomaticOutdoorModeEnabled(boolean enabled) {
        if (!mUseOutdoorMode) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_AUTO_OUTDOOR_MODE, enabled);
        return true;
    }

    boolean isAutomaticOutdoorModeEnabled() {
        return mUseOutdoorMode && (mNightDisplayAvailable ||
                getBoolean(EVSettings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                           getDefaultAutoOutdoorMode()));
    }

    boolean getDefaultAutoOutdoorMode() {
        return mDefaultAutoOutdoorMode;
    }
}
