/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.content.Context;
import android.hardware.display.ColorDisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Range;
import android.util.Slog;
import android.util.SparseArray;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;

import evervolv.hardware.DisplayMode;
import evervolv.hardware.HSIC;
import evervolv.hardware.LiveDisplayManager;
import evervolv.provider.EVSettings;

import vendor.lineage.livedisplay.V2_0.IDisplayModes;
import vendor.lineage.livedisplay.V2_0.IPictureAdjustment;

public class PictureAdjustmentController extends LiveDisplayFeature {

    private static final String TAG = "PictureAdjustmentController";

    private IPictureAdjustment mPictureAdjustment = null;
    private IDisplayModes mDisplayModes = null;

    private final boolean mUsePictureAdjustment;
    private final boolean mHasDisplayModes;

    // DisplayMode remapping
    private final ArrayMap<String, String> mDisplayModeMappings = new ArrayMap<String, String>();
    private final boolean mFilterDisplayModes;

    private List<Range<Float>> mRanges = new ArrayList<Range<Float>>();

    public PictureAdjustmentController(Context context, Handler handler) {
        super(context, handler);

        try {
            mDisplayModes = IDisplayModes.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }

        // Prefer ColorDisplayManager over LiveDisplay if applicable
        final int[] availableColorModes = mContext.getResources().getIntArray(
                com.android.internal.R.array.config_availableColorModes);
        final boolean colorModesAvailable = mContext.getSystemService(ColorDisplayManager.class).isDeviceColorManaged()
                && !ColorDisplayManager.areAccessibilityTransformsEnabled(mContext) && availableColorModes.length > 0;
        mHasDisplayModes = mDisplayModes != null && !colorModesAvailable;

        final String[] mappings = mContext.getResources().getStringArray(
                com.evervolv.platform.internal.R.array.config_displayModeMappings);
        if (mappings != null && mappings.length > 0) {
            for (String mapping : mappings) {
                String[] split = mapping.split(":");
                if (split.length == 2) {
                    mDisplayModeMappings.put(split[0], split[1]);
                }
            }
        }
        mFilterDisplayModes = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_filterDisplayModes);

        try {
            mPictureAdjustment = IPictureAdjustment.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        boolean usePA = mPictureAdjustment != null;
        if (usePA) {
            List<Range<Float>> r = null;
            try {
                r = Arrays.asList(
                        fromFloatRange(mPictureAdjustment.getHueRange()),
                        fromFloatRange(mPictureAdjustment.getSaturationRange()),
                        fromFloatRange(mPictureAdjustment.getIntensityRange()),
                        fromFloatRange(mPictureAdjustment.getContrastRange()),
                        fromFloatRange(mPictureAdjustment.getSaturationThresholdRange()));
            } catch (RemoteException e) { }

            if (r != null) {
                mRanges.addAll(r);
            }
            if (mRanges.size() < 4) {
                usePA = false;
            } else {
                for (Range<Float> range : mRanges) {
                    if (range.getLower() == 0.0f && range.getUpper() == 0.0f) {
                        usePA = false;
                        break;
                    }
                }
            }
        }
        if (!usePA) {
            mRanges.clear();
        }
        mUsePictureAdjustment = usePA;
    }

    @Override
    public void onStart() {
        if (!mUsePictureAdjustment) {
            return;
        }

        registerSettings(
                EVSettings.System.getUriFor(EVSettings.System.DISPLAY_PICTURE_ADJUSTMENT));
    }

    @Override
    protected void onSettingsChanged(Uri uri) {// nothing to do for mode switch
        updatePictureAdjustment();
    }

    @Override
    protected void onUpdate() {
        updatePictureAdjustment();
    }

    private Range<Float> fromFloatRange(vendor.lineage.livedisplay.V2_0.FloatRange range) {
        return new Range(range.min, range.max);
    }

    private vendor.lineage.livedisplay.V2_0.HSIC toCompat(HSIC hsic) {
        vendor.lineage.livedisplay.V2_0.HSIC h = new vendor.lineage.livedisplay.V2_0.HSIC();
        h.hue = hsic.getHue();
        h.saturation = hsic.getSaturation();
        h.intensity = hsic.getIntensity();
        h.contrast = hsic.getContrast();
        h.saturationThreshold = hsic.getSaturationThreshold();
        return h;
    }

    private HSIC fromCompat(vendor.lineage.livedisplay.V2_0.HSIC hsic) {
        return new HSIC(hsic.hue, hsic.saturation, hsic.intensity,
                hsic.contrast, hsic.saturationThreshold);
    }

    private void updatePictureAdjustment() {
        if (mUsePictureAdjustment && isScreenOn()) {
            final HSIC hsic = getPictureAdjustment();
            if (hsic == null)
                return;

            try {
                mPictureAdjustment.setPictureAdjustment(toCompat(hsic));
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to set picture adjustment! " + hsic.toString());
            }
        }
    }

    @Override
    public void dump(PrintWriter pw) {
        if (mUsePictureAdjustment) {
            pw.println();
            pw.println("PictureAdjustmentController Configuration:");
            pw.println("  adjustment=" + getPictureAdjustment());
            pw.println("  hueRange=" + getHueRange());
            pw.println("  saturationRange=" + getSaturationRange());
            pw.println("  intensityRange=" + getIntensityRange());
            pw.println("  contrastRange=" + getContrastRange());
            pw.println("  saturationThresholdRange=" + getSaturationThresholdRange());
            pw.println("  defaultAdjustment=" + getDefaultPictureAdjustment());
            pw.println("  mHasDisplayModes=" + mHasDisplayModes);
        }
    }

    @Override
    public boolean getCapabilities(BitSet caps) {
        if (mUsePictureAdjustment) {
            caps.set(LiveDisplayManager.FEATURE_PICTURE_ADJUSTMENT);
        }
        if (mHasDisplayModes) {
            caps.set(LiveDisplayManager.FEATURE_DISPLAY_MODES);
        }
        return mUsePictureAdjustment || mHasDisplayModes;
    }

    Range<Float> getHueRange() {
        return mUsePictureAdjustment && mRanges.size() > 0
                ? mRanges.get(0) : Range.create(0.0f, 0.0f);
    }

    Range<Float> getSaturationRange() {
        return mUsePictureAdjustment && mRanges.size() > 1
                ? mRanges.get(1) : Range.create(0.0f, 0.0f);
    }

    Range<Float> getIntensityRange() {
        return mUsePictureAdjustment && mRanges.size() > 2
                ? mRanges.get(2) : Range.create(0.0f, 0.0f);
    }

    Range<Float> getContrastRange() {
        return mUsePictureAdjustment && mRanges.size() > 3 ?
                mRanges.get(3) : Range.create(0.0f, 0.0f);
    }

    Range<Float> getSaturationThresholdRange() {
        return mUsePictureAdjustment && mRanges.size() > 4 ?
                mRanges.get(4) : Range.create(0.0f, 0.0f);
    }

    HSIC getDefaultPictureAdjustment() {
        HSIC hsic = null;
        if (mUsePictureAdjustment) {
            try {
                hsic = fromCompat(mPictureAdjustment.getDefaultPictureAdjustment());
            } catch (RemoteException e) { }
        }
        if (hsic == null) {
            hsic = new HSIC(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        return hsic;
    }

    HSIC getPictureAdjustment() {
        HSIC hsic = null;
        if (mUsePictureAdjustment) {
            int modeID = 0;
            if (mHasDisplayModes) {
                DisplayMode mode = getCurrentDisplayMode();
                if (mode != null) {
                    modeID = mode.id;
                }
            }
            hsic = getPAForMode(modeID);
        }
        if (hsic == null) {
            hsic = new HSIC(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        return hsic;
    }

    boolean setPictureAdjustment(HSIC hsic) {
        if (mUsePictureAdjustment && hsic != null) {
            int modeID = 0;
            if (mHasDisplayModes) {
                DisplayMode mode = getCurrentDisplayMode();
                if (mode != null) {
                    modeID = mode.id;
                }
            }
            setPAForMode(modeID, hsic);
            return true;
        }
        return false;
    }

    // TODO: Expose mode-based settings to upper layers

    private HSIC getPAForMode(int mode) {
        final SparseArray<HSIC> prefs = unpackPreference();
        if (prefs.indexOfKey(mode) >= 0) {
            return prefs.get(mode);
        }
        return getDefaultPictureAdjustment();
    }

    private void setPAForMode(int mode, HSIC hsic) {
        final SparseArray<HSIC> prefs = unpackPreference();
        prefs.put(mode, hsic);
        packPreference(prefs);
    }

    private SparseArray<HSIC> unpackPreference() {
        final SparseArray<HSIC> ret = new SparseArray<HSIC>();

        String pref = getString(EVSettings.System.DISPLAY_PICTURE_ADJUSTMENT);
        if (pref != null) {
            String[] byMode = TextUtils.split(pref, ",");
            for (String mode : byMode) {
                String[] modePA = TextUtils.split(mode, ":");
                if (modePA.length == 2) {
                    ret.put(Integer.valueOf(modePA[0]), HSIC.unflattenFrom(modePA[1]));
                }
            }
        }
        return ret;
    }

    private void packPreference(final SparseArray<HSIC> modes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < modes.size(); i++) {
            int id = modes.keyAt(i);
            HSIC m = modes.get(id);
            if (i > 0) {
                sb.append(",");
            }
            sb.append(id).append(":").append(m.flatten());
        }
        putString(EVSettings.System.DISPLAY_PICTURE_ADJUSTMENT, sb.toString());
    }


    private DisplayMode remapDisplayMode(DisplayMode in) {
        if (in == null) {
            return null;
        }
        if (mDisplayModeMappings.containsKey(in.name)) {
            return new DisplayMode(in.id, mDisplayModeMappings.get(in.name));
        }
        if (!mFilterDisplayModes) {
            return in;
        }
        return null;
    }

    private DisplayMode getDisplayModeCompat(
            vendor.lineage.livedisplay.V2_0.DisplayMode mode) {
        return new DisplayMode(mode.id, mode.name);
    }

    /**
     * @return a list of available display modes on the devices
     */
    DisplayMode[] getDisplayModes() {
        DisplayMode[] availableModes = null;
        try {
            if (mHasDisplayModes) {
                ArrayList<vendor.lineage.livedisplay.V2_0.DisplayMode> modes =
                    mDisplayModes.getDisplayModes();
                int size = modes.size();
                availableModes = new DisplayMode[size];
                for (int i = 0; i < size; i++) {
                    availableModes[i] = getDisplayModeCompat(modes.get(i));
                }
            }
        } catch (RemoteException e) {
        } finally {
            if (availableModes == null) {
                return null;
            }
            final ArrayList<DisplayMode> remapped = new ArrayList<DisplayMode>();
            for (DisplayMode mode : availableModes) {
                DisplayMode r = remapDisplayMode(mode);
                if (r != null) {
                    remapped.add(r);
                }
            }
            return remapped.toArray(new DisplayMode[0]);
        }
    }

    /**
     * @return the default display mode to be set on boot
     */
    DisplayMode getDefaultDisplayMode() {
        DisplayMode mode = null;
        try {
            if (mHasDisplayModes) {
                mode = getDisplayModeCompat(mDisplayModes.getDefaultDisplayMode());
            }
        } catch (RemoteException e) {
        } finally {
            return mode != null ? remapDisplayMode(mode) : null;
        }
    }

    /**
     * @return the currently active display mode
     */
    DisplayMode getCurrentDisplayMode() {
        DisplayMode mode = null;
        try {
            if (mHasDisplayModes) {
                mode = getDisplayModeCompat(mDisplayModes.getCurrentDisplayMode());
            }
        } catch (RemoteException e) {
        } finally {
            return mode != null ? remapDisplayMode(mode) : null;
        }
    }

    /**
     * @return true if setting the mode was successful
     */
    boolean setDisplayMode(DisplayMode mode, boolean makeDefault) {
        try {
            if (mHasDisplayModes) {
                return mDisplayModes.setDisplayMode(mode.id, makeDefault);
            }
        } catch (RemoteException e) {
        }
        return false;
    }
}
