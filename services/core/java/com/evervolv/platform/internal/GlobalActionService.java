/*
 * SPDX-FileCopyrightText: 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.platform.internal;

import static com.evervolv.internal.util.PowerMenuConstants.GLOBAL_ACTION_KEY_BUGREPORT;
import static com.evervolv.internal.util.PowerMenuConstants.GLOBAL_ACTION_KEY_LOCKDOWN;
import static evervolv.provider.EVSettings.Secure.POWER_MENU_ACTIONS;
import static evervolv.provider.EVSettings.Secure.getStringForUser;
import static evervolv.provider.EVSettings.Secure.putStringForUser;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;

import com.evervolv.internal.util.PowerMenuConstants;

import evervolv.app.ContextConstants;
import evervolv.app.IGlobalActions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @hide
 */
public class GlobalActionService extends VendorService {

    private static final String TAG = "GlobalActionService";

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private final List<String> mLocalUserConfig = new ArrayList<String>();

    // Observes user-controlled settings
    private GlobalActionSettingsObserver mObserver;

    public GlobalActionService(Context context) {
        super(context);

        mContext = context;
        mContentResolver = mContext.getContentResolver();
    }

    private class GlobalActionSettingsObserver extends ContentObserver {

        private final Uri BUGREPORT_URI =
                Settings.Global.getUriFor(Settings.Global.BUGREPORT_IN_POWER_MENU);

        public GlobalActionSettingsObserver(Context context, Handler handler) {
            super(handler);
        }

        public void observe(boolean enabled) {
            if (enabled) {
                mContentResolver.registerContentObserver(BUGREPORT_URI, false, this);
            } else {
                mContentResolver.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateUserConfigInternal(Settings.Global.getInt(mContentResolver,
                    Settings.Global.BUGREPORT_IN_POWER_MENU, 0) == 1,
                    GLOBAL_ACTION_KEY_BUGREPORT);
        }
    };

    private void populateUserConfig() {
        mLocalUserConfig.clear();
        mLocalUserConfig.addAll(Arrays.asList(getUserConfig()));
    }

    private String[] getUserConfig() {
        String savedActions = getStringForUser(mContentResolver,
                POWER_MENU_ACTIONS, UserHandle.USER_CURRENT);

        if (savedActions == null) {
            return mContext.getResources().getStringArray(
                    com.android.internal.R.array.config_globalActionsList);
        } else {
            return savedActions.split("\\|");
        }
    }

    private void updateUserConfigInternal(boolean enabled, String action) {
        if (enabled) {
            if (!mLocalUserConfig.contains(action)) {
                mLocalUserConfig.add(action);
            }
        } else {
            if (mLocalUserConfig.contains(action)) {
                mLocalUserConfig.remove(action);
            }
        }
        saveUserConfig();
    }

    private void saveUserConfig() {
        List<String> actions = new ArrayList<String>();
        for (String action : PowerMenuConstants.getAllActions()) {
            if (mLocalUserConfig.contains(action)) {
                actions.add(action);
            }
        }

        String s = String.join("|", actions);
        putStringForUser(mContentResolver, POWER_MENU_ACTIONS, s, UserHandle.USER_CURRENT);
    }

    @Override
    public String getFeatureDeclaration() {
        return ContextConstants.Features.GLOBAL_ACTIONS;
    }

    @Override
    public void onStart() {
        publishBinderService(ContextConstants.GLOBAL_ACTIONS_SERVICE, mBinder);
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_BOOT_COMPLETED) {
            populateUserConfig();

            mObserver = new GlobalActionSettingsObserver(mContext, null);
            mObserver.observe(true);
        }
    }

    private final IBinder mBinder = new IGlobalActions.Stub() {

        @Override
        public void updateUserConfig(boolean enabled, String action) {
            updateUserConfigInternal(enabled, action);
        }

        @Override
        public List<String> getLocalUserConfig() {
            populateUserConfig();
            return mLocalUserConfig;
        }

        @Override
        public String[] getUserActionsArray() {
            return getUserConfig();
        }

        @Override
        public boolean userConfigContains(String preference) {
            return getLocalUserConfig().contains(preference);
        }
    };
}
