/*
 * Copyright (C) 2016 The CyanogenMod Project
 *               2018-2021 The LineageOS Project
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

import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.MathUtils;
import android.util.Slog;
import android.view.animation.LinearInterpolator;

import com.android.internal.util.ArrayUtils;
import com.android.server.display.color.DisplayTransformManager;
import com.android.server.LocalServices;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.NoSuchElementException;

import evervolv.hardware.LiveDisplayManager;
import evervolv.provider.EVSettings;

import vendor.lineage.livedisplay.V2_0.IAdaptiveBacklight;
import vendor.lineage.livedisplay.V2_0.IAutoContrast;
import vendor.lineage.livedisplay.V2_0.IColorEnhancement;
import vendor.lineage.livedisplay.V2_0.IDisplayColorCalibration;
import vendor.lineage.livedisplay.V2_0.IReadingEnhancement;
import vendor.lineage.livedisplay.V2_1.IAntiFlicker;

import static com.android.server.display.color.DisplayTransformManager.LEVEL_COLOR_MATRIX_NIGHT_DISPLAY;
import static com.android.server.display.color.DisplayTransformManager.LEVEL_COLOR_MATRIX_GRAYSCALE;

public class DisplayHardwareController extends LiveDisplayFeature {

    private static final String TAG = "DisplayHardwareController";

    private IAdaptiveBacklight mAdaptiveBacklight = null;
    private IAntiFlicker mAntiFlicker = null;
    private IAutoContrast mAutoContrast = null;
    private IColorEnhancement mColorEnhancement = null;
    private IDisplayColorCalibration mDisplayColorCalibration = null;
    private IReadingEnhancement mReadingEnhancement = null;

    // hardware capabilities
    private final boolean mUseAutoContrast;
    private final boolean mUseColorAdjustment;
    private final boolean mUseColorEnhancement;
    private final boolean mUseCABC;
    private final boolean mUseReaderMode;

    // default values
    private final boolean mDefaultAutoContrast;
    private final boolean mDefaultColorEnhancement;
    private final boolean mDefaultCABC;

    // color adjustment holders
    private final float[] mAdditionalAdjustment = getDefaultAdjustment();
    private final float[] mColorAdjustment = getDefaultAdjustment();

    private ValueAnimator mAnimator;

    private final int mMaxColor;

    // settings uris
    private static final Uri DISPLAY_AUTO_CONTRAST =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_AUTO_CONTRAST);
    private static final Uri DISPLAY_COLOR_ADJUSTMENT =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_COLOR_ADJUSTMENT);
    private static final Uri DISPLAY_COLOR_ENHANCE =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_COLOR_ENHANCE);
    private static final Uri DISPLAY_CABC =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_CABC);
    private static final Uri DISPLAY_READING_MODE =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_READING_MODE);

    private final DisplayTransformManager mTransformManager;
    private final boolean mAcceleratedTransform;

    /**
     * Matrix and offset used for converting color to grayscale.
     * Copied from com.android.server.accessibility.DisplayAdjustmentUtils.MATRIX_GRAYSCALE
     */
    private final float[] MATRIX_GRAYSCALE = {
            .2126f, .2126f, .2126f, 0,
            .7152f, .7152f, .7152f, 0,
            .0722f, .0722f, .0722f, 0,
                 0,      0,      0, 1
    };

    /** Full color matrix and offset */
    private final float[] MATRIX_NORMAL = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private static final int LEVEL_COLOR_MATRIX_CALIB = LEVEL_COLOR_MATRIX_NIGHT_DISPLAY + 1;
    private static final int LEVEL_COLOR_MATRIX_READING = LEVEL_COLOR_MATRIX_GRAYSCALE + 1;

    private static final int COLOR_CALIBRATION_MIN_INDEX = 3;
    private static final int COLOR_CALIBRATION_MAX_INDEX = 4;

    private static final int CALIBRATION_MIN = 0;
    private static final int CALIBRATION_MAX = 255;

    private int[] mCurColors = { CALIBRATION_MAX, CALIBRATION_MAX, CALIBRATION_MAX };

    public DisplayHardwareController(Context context, Handler handler) {
        super(context, handler);

        mAcceleratedTransform = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_setColorTransformAccelerated);
        mTransformManager = LocalServices.getService(DisplayTransformManager.class);

        try {
            mAdaptiveBacklight = IAdaptiveBacklight.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseCABC = mAdaptiveBacklight != null;
        mDefaultCABC = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultCABC);

        try {
            mColorEnhancement = IColorEnhancement.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseColorEnhancement = mColorEnhancement != null;
        mDefaultColorEnhancement = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultColorEnhancement);

        try {
            mAutoContrast = IAutoContrast.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseAutoContrast = mAutoContrast != null;
        mDefaultAutoContrast = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultAutoContrast);

        try {
            mDisplayColorCalibration = IDisplayColorCalibration.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseColorAdjustment = mDisplayColorCalibration != null;

        try {
            mReadingEnhancement = IReadingEnhancement.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseReaderMode = mReadingEnhancement != null;

        if (mUseColorAdjustment || mAcceleratedTransform) {
            mMaxColor = getDisplayColorCalibrationMax();
            copyColors(getColorAdjustment(), mColorAdjustment);
        } else {
            mMaxColor = 0;
        }
    }

    @Override
    public void onStart() {
        final ArrayList<Uri> settings = new ArrayList<Uri>();

        if (mUseCABC) {
            settings.add(DISPLAY_CABC);
        }
        if (mUseColorEnhancement) {
            settings.add(DISPLAY_COLOR_ENHANCE);
        }
        if (mUseAutoContrast) {
            settings.add(DISPLAY_AUTO_CONTRAST);
        }
        if (mUseColorAdjustment) {
            settings.add(DISPLAY_COLOR_ADJUSTMENT);
        }
        if (mUseReaderMode || mAcceleratedTransform) {
            settings.add(DISPLAY_READING_MODE);
        }

        if (settings.size() == 0) {
            return;
        }

        registerSettings(settings.toArray(new Uri[0]));
    }

    @Override
    public boolean getCapabilities(final BitSet caps) {
        if (mUseAutoContrast) {
            caps.set(LiveDisplayManager.FEATURE_AUTO_CONTRAST);
        }
        if (mUseColorEnhancement) {
            caps.set(LiveDisplayManager.FEATURE_COLOR_ENHANCEMENT);
        }
        if (mUseCABC) {
            caps.set(LiveDisplayManager.FEATURE_CABC);
        }
        if (mUseColorAdjustment) {
            caps.set(LiveDisplayManager.FEATURE_COLOR_ADJUSTMENT);
        }
        if (mUseReaderMode || mAcceleratedTransform) {
            caps.set(LiveDisplayManager.FEATURE_READING_ENHANCEMENT);
        }
        return mUseAutoContrast || mUseColorEnhancement || mUseCABC || mUseColorAdjustment ||
            mUseReaderMode || mAcceleratedTransform;
    }

    @Override
    public synchronized void onSettingsChanged(Uri uri) {
        if (uri == null || uri.equals(DISPLAY_CABC)) {
            updateCABCMode();
        }
        if (uri == null || uri.equals(DISPLAY_AUTO_CONTRAST)) {
            updateAutoContrast();
        }
        if (uri == null || uri.equals(DISPLAY_COLOR_ENHANCE)) {
            updateColorEnhancement();
        }
        if (uri == null || uri.equals(DISPLAY_COLOR_ADJUSTMENT)) {
            copyColors(getColorAdjustment(), mColorAdjustment);
            updateColorAdjustment();
        }
        if (uri == null || uri.equals(DISPLAY_READING_MODE)) {
            updateReadingMode();
        }
    }

    private synchronized void updateHardware() {
        if (isScreenOn()) {
            updateCABCMode();
            updateAutoContrast();
            updateColorEnhancement();
            updateReadingMode();
        }
    }

    @Override
    protected void onUpdate() {
        updateHardware();
    }

    @Override
    protected synchronized void onScreenStateChanged() {
        if (mUseColorAdjustment) {
            if (mAnimator != null && mAnimator.isRunning() && !isScreenOn()) {
                mAnimator.cancel();
            } else if (isScreenOn()) {
                updateColorAdjustment();
            }
        }
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("DisplayHardwareController Configuration:");
        pw.println("  mUseAutoContrast=" + mUseAutoContrast);
        pw.println("  mUseColorAdjustment=" + mUseColorAdjustment);
        pw.println("  mUseColorEnhancement="  + mUseColorEnhancement);
        pw.println("  mUseCABC=" + mUseCABC);
        pw.println("  mUseReaderMode=" + (mUseReaderMode || mAcceleratedTransform));
        pw.println();
        pw.println("  DisplayHardwareController State:");
        pw.println("    mAutoContrast=" + isAutoContrastEnabled());
        pw.println("    mColorEnhancement=" + isColorEnhancementEnabled());
        pw.println("    mCABC=" + isCABCEnabled());
        pw.println("    mColorAdjustment=" + Arrays.toString(mColorAdjustment));
        pw.println("    mAdditionalAdjustment=" + Arrays.toString(mAdditionalAdjustment));
        pw.println("    hardware setting=" + Arrays.toString(getDisplayColorCalibration()));
    }

    /**
     * Automatic contrast optimization
     */
    private void updateAutoContrast() {
        if (!mUseAutoContrast) {
            return;
        }

        try {
            mAutoContrast.setEnabled(isAutoContrastEnabled());
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    /**
     * Color enhancement is optional
     */
    private void updateColorEnhancement() {
        if (!mUseColorEnhancement) {
            return;
        }

        try {
            mColorEnhancement.setEnabled(
                    (!isLowPowerMode() || mDefaultColorEnhancement) && isColorEnhancementEnabled());
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    /**
     * Adaptive backlight / low power mode. Turn it off when under very bright light.
     */
    private void updateCABCMode() {
        if (!mUseCABC) {
            return;
        }

        try {
            mAdaptiveBacklight.setEnabled(isCABCEnabled());
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    /**
     * Reading enhancement is optional
     */
    private void updateReadingMode() {
        if (mUseReaderMode) {
            try {
                mReadingEnhancement.setEnabled(isReadingModeEnabled());
            } catch (NoSuchElementException | RemoteException e) {
            }
        } else if (mAcceleratedTransform) {
            mTransformManager.setColorMatrix(LEVEL_COLOR_MATRIX_READING,
                    isReadingModeEnabled() ? MATRIX_GRAYSCALE : MATRIX_NORMAL);
        }

    }

    private synchronized void updateColorAdjustment() {
        if (!mUseColorAdjustment) {
            return;
        }

        final float[] rgb = getDefaultAdjustment();

        copyColors(mColorAdjustment, rgb);
        rgb[0] *= mAdditionalAdjustment[0];
        rgb[1] *= mAdditionalAdjustment[1];
        rgb[2] *= mAdditionalAdjustment[2];

        if (DEBUG) {
            Slog.d(TAG, "updateColorAdjustment: " + Arrays.toString(rgb));
        }

        if (validateColors(rgb)) {
            animateDisplayColor(rgb);
        }
    }

    /**
     * Smoothly animate the current display colors to the new value.
     */
    private synchronized void animateDisplayColor(float[] targetColors) {

        // always start with the current values in the hardware
        int[] currentInts = getDisplayColorCalibration();
        float[] currentColors = new float[] {
                (float)currentInts[0] / (float)mMaxColor,
                (float)currentInts[1] / (float)mMaxColor,
                (float)currentInts[2] / (float)mMaxColor };

        if (currentColors[0] == targetColors[0] &&
                currentColors[1] == targetColors[1] &&
                currentColors[2] == targetColors[2]) {
            return;
        }

        // max 500 ms, scaled vs. the largest delta
        long duration = (long)(750 * (Math.max(Math.max(
                Math.abs(currentColors[0] - targetColors[0]),
                Math.abs(currentColors[1] - targetColors[1])),
                Math.abs(currentColors[2] - targetColors[2]))));

        if (DEBUG) {
            Slog.d(TAG, "animateDisplayColor current=" + Arrays.toString(currentColors) +
                    " targetColors=" + Arrays.toString(targetColors) + " duration=" + duration);
        }

        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator.removeAllUpdateListeners();
        }

        mAnimator = ValueAnimator.ofObject(
                new FloatArrayEvaluator(new float[CALIBRATION_MIN]), currentColors, targetColors);
        mAnimator.setDuration(duration);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                synchronized (DisplayHardwareController.this) {
                    if (isScreenOn()) {
                        float[] value = (float[]) animation.getAnimatedValue();
                        setDisplayColorCalibration(new int[] {
                                (int) (value[0] * mMaxColor),
                                (int) (value[1] * mMaxColor),
                                (int) (value[2] * mMaxColor)
                        });
                        screenRefresh();
                    }
                }
            }
        });
        mAnimator.start();
    }

    /**
     * Tell SurfaceFlinger to repaint the screen. This is called after updating
     * hardware registers for display calibration to have an immediate effect.
     */
    private void screenRefresh() {
        try {
            final IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                final Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                flinger.transact(1004, data, null, 0);
                data.recycle();
            }
        } catch (RemoteException ex) {
            Slog.e(TAG, "Failed to refresh screen", ex);
        }
    }

    /**
     * Ensure all values are within range
     *
     * @param colors
     * @return true if valid
     */
    private boolean validateColors(float[] colors) {
        if (colors == null || colors.length != CALIBRATION_MIN) {
            return false;
        }

        for (int i = 0; i < CALIBRATION_MIN; i++) {
            colors[i] = MathUtils.constrain(colors[i], 0.0f, 1.0f);
        }
        return true;
    }

    /**
     * Parse and sanity check an RGB triplet from a string.
     */
    private boolean parseColorAdjustment(String rgbString, float[] dest) {
        String[] adj = rgbString == null ? null : rgbString.split(" ");

        if (adj == null || adj.length != CALIBRATION_MIN || dest == null || dest.length != 3) {
            return false;
        }

        try {
            dest[0] = Float.parseFloat(adj[0]);
            dest[1] = Float.parseFloat(adj[1]);
            dest[2] = Float.parseFloat(adj[2]);
        } catch (NumberFormatException e) {
            Slog.e(TAG, e.getMessage(), e);
            return false;
        }

        // sanity check
        return validateColors(dest);
    }

    /**
     * Additional adjustments provided by night mode
     *
     * @param adj
     */
    synchronized boolean setAdditionalAdjustment(float[] adj) {
        if (!mUseColorAdjustment) {
            return false;
        }

        if (DEBUG) {
            Slog.d(TAG, "setAdditionalAdjustment: " + Arrays.toString(adj));
        }

        // Sanity check this so we don't mangle the display
        if (validateColors(adj)) {
            copyColors(adj, mAdditionalAdjustment);
            updateColorAdjustment();
            return true;
        }
        return false;
    }

    boolean getDefaultCABC() {
        return mDefaultCABC;
    }

    boolean getDefaultAutoContrast() {
        return mDefaultAutoContrast;
    }

    boolean getDefaultColorEnhancement() {
        return mDefaultColorEnhancement;
    }

    boolean isAutoContrastEnabled() {
        return mUseAutoContrast &&
                getBoolean(EVSettings.System.DISPLAY_AUTO_CONTRAST, mDefaultAutoContrast);
    }

    boolean setAutoContrastEnabled(boolean enabled) {
        if (!mUseAutoContrast) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_AUTO_CONTRAST, enabled);
        return true;
    }

    boolean isCABCEnabled() {
        return mUseCABC &&
                getBoolean(EVSettings.System.DISPLAY_CABC, mDefaultCABC);
    }

    boolean setCABCEnabled(boolean enabled) {
        if (!mUseCABC) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_CABC, enabled);
        return true;
    }

    boolean isColorEnhancementEnabled() {
        return mUseColorEnhancement &&
                getBoolean(EVSettings.System.DISPLAY_COLOR_ENHANCE,
                mDefaultColorEnhancement);
    }

    boolean setColorEnhancementEnabled(boolean enabled) {
        if (!mUseColorEnhancement) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_COLOR_ENHANCE, enabled);
        return true;
    }

    boolean isReadingModeEnabled() {
        return (mUseReaderMode || mAcceleratedTransform) &&
                getBoolean(EVSettings.System.DISPLAY_READING_MODE, false);
    }

    boolean setReadingModeEnabled(boolean enabled) {
        if (!mUseReaderMode && !mAcceleratedTransform) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_READING_MODE, enabled);
        return true;
    }

    float[] getColorAdjustment() {
        if (!mUseColorAdjustment) {
            return getDefaultAdjustment();
        }
        float[] cur = new float[CALIBRATION_MIN];
        if (!parseColorAdjustment(getString(EVSettings.System.DISPLAY_COLOR_ADJUSTMENT), cur)) {
            // clear it out if invalid
            cur = getDefaultAdjustment();
            saveColorAdjustmentString(cur);
        }
        return cur;
    }

    boolean setColorAdjustment(float[] adj) {
        // sanity check
        if (!mUseColorAdjustment || !validateColors(adj)) {
            return false;
        }
        saveColorAdjustmentString(adj);
        return true;
    }

    private void saveColorAdjustmentString(final float[] adj) {
        StringBuilder sb = new StringBuilder();
        sb.append(adj[0]).append(" ").append(adj[1]).append(" ").append(adj[2]);
        putString(EVSettings.System.DISPLAY_COLOR_ADJUSTMENT, sb.toString());
    }

    boolean hasColorAdjustment() {
        return mUseColorAdjustment;
    }

    private static float[] getDefaultAdjustment() {
        return new float[] { 1.0f, 1.0f, 1.0f };
    }

    private void copyColors(float[] src, float[] dst) {
        if (src != null && dst != null && src.length == CALIBRATION_MIN && dst.length == CALIBRATION_MIN) {
            dst[0] = src[0];
            dst[1] = src[1];
            dst[2] = src[2];
        }
    }

    private float[] rgbToMatrix(int[] rgb) {
        float[] mat = new float[16];

        for (int i = 0; i < CALIBRATION_MIN; i++) {
            // Sanity check
            if (rgb[i] > CALIBRATION_MAX)
                rgb[i] = CALIBRATION_MAX;
            else if (rgb[i] < CALIBRATION_MIN)
                rgb[i] = CALIBRATION_MIN;

            mat[i * 5] = (float)rgb[i] / (float)CALIBRATION_MAX;
        }

        mat[15] = 1.0f;
        return mat;
    }

    private int[] getDisplayColorCalibrationArray() {
        if (mUseColorAdjustment) {
            try {
                return ArrayUtils.convertToIntArray(mDisplayColorCalibration.getCalibration());
            } catch (RemoteException e) {
            }
        }

        int[] rgb = mAcceleratedTransform ? mCurColors : null;
        if (rgb == null || rgb.length < CALIBRATION_MIN) {
            Slog.e(TAG, "Invalid color calibration string");
            return null;
        }

        int[] currentCalibration = new int[CALIBRATION_MAX + 1];
        for (int i = 0; i < COLOR_CALIBRATION_MIN_INDEX; i++) {
            currentCalibration[i] = rgb[i];
        }
        currentCalibration[COLOR_CALIBRATION_MIN_INDEX] = CALIBRATION_MIN;
        currentCalibration[COLOR_CALIBRATION_MAX_INDEX] = CALIBRATION_MAX;
        return currentCalibration;
    }

    int[] getDisplayColorCalibration() {
        if (!mUseColorAdjustment && !mAcceleratedTransform) {
            return null;
        }

        int[] arr = getDisplayColorCalibrationArray();
        if (arr == null || arr.length < CALIBRATION_MIN) {
            return null;
        }
        return Arrays.copyOf(arr, CALIBRATION_MIN);
    }

    private int getArrayValue(int[] arr, int idx, int defaultValue) {
        if (arr == null || arr.length <= idx) {
            return defaultValue;
        }

        return arr[idx];
    }

    /**
     * @return The minimum value for all colors
     */
    int getDisplayColorCalibrationMin() {
        if (mUseColorAdjustment) {
            try {
                return mDisplayColorCalibration.getMinValue();
            } catch (RemoteException e) {
                return 0;
            }
        }
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return The maximum value for all colors
     */
    int getDisplayColorCalibrationMax() {
        if (mUseColorAdjustment) {
            try {
                return mDisplayColorCalibration.getMaxValue();
            } catch (RemoteException e) {
                return 0;
            }
        }
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MAX_INDEX, 0);
    }

    boolean setDisplayColorCalibration(int[] rgb) {
        if (mUseColorAdjustment) {
            try {
                return mDisplayColorCalibration.setCalibration(
                       new ArrayList<Integer>(Arrays.asList(rgb[0], rgb[1], rgb[2])));
            } catch (RemoteException e) { }
        } else if (mAcceleratedTransform) {
            mCurColors = rgb;
            mTransformManager.setColorMatrix(LEVEL_COLOR_MATRIX_CALIB, rgbToMatrix(rgb));
            return true;
        }
        return false;
    }
}
