/*
 * SPDX-FileCopyrightText: 2017 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.internal.util;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.UserHandle;

import evervolv.provider.EVSettings;

public final class PowerMenuUtils {
    public static boolean isAdvancedRestartPossible(final Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        boolean keyguardLocked = km.inKeyguardRestrictedInputMode() && km.isKeyguardSecure();
        boolean advancedRestartEnabled = EVSettings.Secure.getInt(context.getContentResolver(),
                EVSettings.Secure.ADVANCED_REBOOT, 0) == 1;
        boolean isPrimaryUser = UserHandle.getCallingUserId() == UserHandle.USER_SYSTEM;

        return advancedRestartEnabled && !keyguardLocked && isPrimaryUser;
    }
}
