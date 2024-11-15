/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.platform.internal;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.SystemServiceManager;

import com.evervolv.platform.internal.common.VendorServiceHelper;

/**
 * Base Vendor System Server which handles the starting and states of various Lineage
 * specific system services. Since its part of the main looper provided by the system
 * server, it will be available indefinitely (until all the things die).
 */
public class VendorServer {
    private static final String TAG = "VendorServer";
    private final Context mSystemContext;
    private final VendorServiceHelper mSystemServiceHelper;

    private static final String ENCRYPTING_STATE = "trigger_restart_min_framework";
    private static final String ENCRYPTED_STATE = "1";

    public VendorServer(Context systemContext) {
        mSystemContext = systemContext;
        mSystemServiceHelper = new VendorServiceHelper(mSystemContext);
    }

    public static boolean coreAppsOnly() {
        // Only run "core" apps+services if we're encrypting the device.
        final String cryptState = SystemProperties.get("vold.decrypt");
        final boolean isAlarmBoot = SystemProperties.getBoolean("ro.alarm_boot", false) ||
                SystemProperties.getBoolean("ro.vendor.alarm_boot", false);
        return ENCRYPTING_STATE.equals(cryptState) ||
               ENCRYPTED_STATE.equals(cryptState) ||
               isAlarmBoot;
    }

    /**
     * Invoked via reflection by the SystemServer
     */
    private void run() {
        // Start services.
        try {
            startServices();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting vendor system services", ex);
            throw ex;
        }
    }

    private void startServices() {
        final Context context = mSystemContext;
        final SystemServiceManager ssm = LocalServices.getService(SystemServiceManager.class);
        String[] externalServices = context.getResources().getStringArray(
                com.evervolv.platform.internal.R.array.config_externalVendorServices);

        for (String service : externalServices) {
            try {
                Slog.i(TAG, "Attempting to start service " + service);
                VendorService vendorService =  mSystemServiceHelper.getServiceFor(service);
                if (context.getPackageManager().hasSystemFeature(
                        vendorService.getFeatureDeclaration())) {
                    if (coreAppsOnly() && !vendorService.isCoreService()) {
                        Slog.d(TAG, "Not starting " + service +
                                " - only parsing core apps");
                    } else {
                        Slog.i(TAG, "Starting service " + service);
                        ssm.startService(vendorService.getClass());
                    }
                } else {
                    Slog.i(TAG, "Not starting service " + service +
                            " due to feature not declared on device");
                }
            } catch (Throwable e) {
                reportWtf("starting " + service , e);
            }
        }
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }
}
