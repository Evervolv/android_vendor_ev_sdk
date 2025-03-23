/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.evervolv.platform.internal.display;

import static com.evervolv.platform.internal.display.LiveDisplayService.ALL_CHANGED;
import static com.evervolv.platform.internal.display.LiveDisplayService.DISPLAY_CHANGED;
import static com.evervolv.platform.internal.display.LiveDisplayService.MODE_CHANGED;
import static com.evervolv.platform.internal.display.LiveDisplayService.TWILIGHT_CHANGED;

import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.os.Handler;
import android.util.Log;

import com.android.server.LocalServices;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightState;

import com.evervolv.platform.internal.VendorBaseFeature;
import com.evervolv.platform.internal.display.LiveDisplayService.State;

import java.util.BitSet;

public abstract class LiveDisplayFeature extends VendorBaseFeature {

    protected static final String TAG = "LiveDisplay";
    protected static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    protected final boolean mNightDisplayAvailable;
    protected final TwilightManager mTwilightManager;

    private State mState;

    public LiveDisplayFeature(Context context, Handler handler) {
        super(context, handler);
        mNightDisplayAvailable = ColorDisplayManager.isNightDisplayAvailable(mContext);
        mTwilightManager = LocalServices.getService(TwilightManager.class);
    }

    public abstract boolean getCapabilities(final BitSet caps);

    protected abstract void onUpdate();

    void update(final int flags, final State state) {
        mState = state;
        if ((flags & DISPLAY_CHANGED) != 0) {
            onScreenStateChanged();
        }
        if (((flags & TWILIGHT_CHANGED) != 0) && mState.mTwilight != null) {
            onTwilightUpdated();
        }
        if ((flags & MODE_CHANGED) != 0) {
            onUpdate();
        }
        if (flags == ALL_CHANGED) {
            onSettingsChanged(null);
        }
    }

    protected void onScreenStateChanged() { }

    protected void onTwilightUpdated() { }

    protected final boolean isLowPowerMode() {
        return mState.mLowPowerMode;
    }

    protected final int getMode() {
        return mState.mMode;
    }

    protected final boolean isScreenOn() {
        return mState.mScreenOn;
    }

    protected final TwilightState getTwilight() {
        return mState.mTwilight;
    }

    public final boolean isNight() {
        return mState.mTwilight != null && mState.mTwilight.isNight();
    }
}
