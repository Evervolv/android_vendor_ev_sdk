/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.platform.internal.health;

import android.content.Context;
import android.os.Handler;

import com.evervolv.platform.internal.VendorBaseFeature;

public abstract class VendorHealthFeature extends VendorBaseFeature {
    protected static final String TAG = "VendorHealth";

    public VendorHealthFeature(Context context, Handler handler) {
        super(context, handler);
    }

    public abstract boolean isSupported();
}
