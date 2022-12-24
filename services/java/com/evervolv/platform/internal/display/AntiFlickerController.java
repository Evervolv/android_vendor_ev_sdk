/*
 * Copyright (C) 2020 Paranoid Android
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
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.NoSuchElementException;

import evervolv.hardware.LiveDisplayManager;
import evervolv.provider.EVSettings;

import java.util.ArrayList;

import vendor.lineage.livedisplay.V2_1.IAntiFlicker;

public class AntiFlickerController extends LiveDisplayFeature {

    private static final String TAG = "AntiFlickerController";

    // hardware capabilities
    private IAntiFlicker mAntiFlicker = null;
    private final boolean mUseAntiFlicker;
    private final boolean mDefaultAntiFlicker;

    private boolean mAntiFlickerEnabled;

    // settings uris
    private static final Uri DISPLAY_ANTI_FLICKER =
            EVSettings.System.getUriFor(EVSettings.System.DISPLAY_ANTI_FLICKER);

    public AntiFlickerController(Context context, Handler handler) {
        super(context, handler);

        try {
            mAntiFlicker = IAntiFlicker.getService();
        } catch (NoSuchElementException | RemoteException e) {
        }
        mUseAntiFlicker = mAntiFlicker != null;

        mDefaultAntiFlicker = mContext.getResources().getBoolean(
                com.evervolv.platform.internal.R.bool.config_defaultAntiFlicker);
    }

    @Override
    public void onStart() {
        final ArrayList<Uri> settings = new ArrayList<Uri>();

        if (mUseAntiFlicker) {
            settings.add(DISPLAY_ANTI_FLICKER);
        }

        if (settings.size() == 0) {
            return;
        }

        registerSettings(settings.toArray(new Uri[0]));
    }

    @Override
    public boolean getCapabilities(final BitSet caps) {
        if (mUseAntiFlicker) {
            caps.set(LiveDisplayManager.FEATURE_ANTI_FLICKER);
        }
        return mUseAntiFlicker;
    }

    @Override
    protected synchronized void onSettingsChanged(Uri uri) {
        if (uri == null || uri.equals(DISPLAY_ANTI_FLICKER)) {
            updateAntiFlicker();
        }
    }

    @Override
    protected void onUpdate() {
        updateAntiFlicker();
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("AntiFlickerController Configuration:");
        pw.println("  mUseAntiFlicker=" + mUseAntiFlicker);
        pw.println();
        pw.println("  AntiFlickerController State:");
        pw.println("    mAntiFlickerEnabled=" + isAntiFlickerEnabled());
    }

    private void updateAntiFlicker() {
        if (!mUseAntiFlicker) {
            return;
        }

        try {
            mAntiFlicker.setEnabled(isAntiFlickerEnabled());
        } catch (NoSuchElementException | RemoteException e) {
        }
    }

    boolean isAntiFlickerEnabled() {
        return mUseAntiFlicker &&
                getBoolean(EVSettings.System.DISPLAY_ANTI_FLICKER, mDefaultAntiFlicker);
    }

    boolean setAntiFlickerEnabled(boolean enabled) {
        if (!mUseAntiFlicker) {
            return false;
        }
        putBoolean(EVSettings.System.DISPLAY_ANTI_FLICKER, enabled);
        return true;
    }
}
