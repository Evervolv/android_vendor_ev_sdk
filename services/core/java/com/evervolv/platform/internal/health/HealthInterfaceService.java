/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.platform.internal.health;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.android.server.ServiceThread;

import com.evervolv.platform.internal.VendorService;

import evervolv.app.ContextConstants;
import evervolv.health.IHealthInterface;
import vendor.lineage.health.ChargingControlSupportedMode;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class HealthInterfaceService extends VendorService {

    private static final String TAG = "VendorHealth";
    private final Context mContext;
    private final Handler mHandler;
    private final ServiceThread mHandlerThread;

    private final List<VendorHealthFeature> mFeatures = new ArrayList<VendorHealthFeature>();

    // Health features
    private ChargingControlController mCCC;

    public HealthInterfaceService(Context context) {
        super(context);
        mContext = context;

        mHandlerThread = new ServiceThread(TAG, Process.THREAD_PRIORITY_DEFAULT, false);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public String getFeatureDeclaration() {
        return ContextConstants.Features.HEALTH;
    }

    @Override
    public boolean isCoreService() {
        return false;
    }

    @Override
    public void onStart() {
        if (!mContext.getPackageManager().hasSystemFeature(
                ContextConstants.Features.HEALTH)) {
            Log.wtf(TAG, "Vendor Health service started by system server but feature xml "
                    + "not declared. Not publishing binder service!");
            return;
        }
        mCCC = new ChargingControlController(mContext, mHandler);
        if (mCCC.isSupported()) {
            mFeatures.add(mCCC);
        }

        if (!mFeatures.isEmpty()) {
            publishBinderService(ContextConstants.HEALTH_INTERFACE, mService);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != PHASE_BOOT_COMPLETED) {
            return;
        }

        // start and update all features
        for (VendorHealthFeature feature : mFeatures) {
            feature.start();
        }
    }

    /* Service */
    private final IBinder mService = new IHealthInterface.Stub() {
        @Override
        public boolean isChargingControlSupported() {
            return mCCC.isSupported();
        }

        @Override
        public boolean getChargingControlEnabled() {
            return mCCC.isEnabled();
        }

        @Override
        public boolean setChargingControlEnabled(boolean enabled) {
            return mCCC.setEnabled(enabled);
        }

        @Override
        public int getChargingControlMode() {
            return mCCC.getMode();
        }

        @Override
        public boolean setChargingControlMode(int mode) {
            return mCCC.setMode(mode);
        }

        @Override
        public int getChargingControlStartTime() {
            return mCCC.getStartTime();
        }

        @Override
        public boolean setChargingControlStartTime(int startTime) {
            return mCCC.setStartTime(startTime);
        }

        @Override
        public int getChargingControlTargetTime() {
            return mCCC.getTargetTime();
        }

        @Override
        public boolean setChargingControlTargetTime(int targetTime) {
            return mCCC.setTargetTime(targetTime);
        }

        @Override
        public int getChargingControlLimit() {
            return mCCC.getLimit();
        }

        @Override
        public boolean setChargingControlLimit(int limit) {
            return mCCC.setLimit(limit);
        }

        @Override
        public boolean resetChargingControl() {
            return mCCC.reset();
        }

        @Override
        public boolean allowFineGrainedSettings() {
            // We allow fine-grained settings if allow toggle and bypass
            return mCCC.isChargingModeSupported(ChargingControlSupportedMode.TOGGLE);
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            mContext.enforceCallingOrSelfPermission(Manifest.permission.DUMP, TAG);

            pw.println();
            pw.println("VendorHealth Service State:");

            for (VendorHealthFeature feature : mFeatures) {
                feature.dump(pw);
            }
        }
    };
}
