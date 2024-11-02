/*
 * SPDX-FileCopyrightText: 2016, The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.internal.logging;

import com.android.internal.logging.MetricsLogger;

/**
 * Serves as a central location for logging constants that is android release agnostic.
 */
public class EVMetricsLogger extends MetricsLogger {
    private static final int BASE = -Integer.MAX_VALUE;

    //Since we never want to collide, lets start at the back and move inward
    public static final int DONT_LOG = BASE + 1;

    // OPEN: QS Location detail panel
    // CATEGORY: QUICK_SETTINGS
    public static final int QS_LOCATION_DETAILS = BASE + 2;

    // OPEN: QS Expanded desktop tile
    // CATEGORY: QUICK_SETTINGS
    public static final int QS_EXPANDED_DESKTOP = BASE + 3;

    // OPEN: QS Powershare tile
    // CATEGORY: QUICK_SETTINGS
    public static final int TILE_POWERSHARE = BASE + 4;
}
