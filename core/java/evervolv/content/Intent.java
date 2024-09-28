/*
 * Copyright (C) 2015 The CyanogenMod Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
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

package evervolv.content;

import android.Manifest;

/**
 * Evervolv specific intent definition class.
 */
public class Intent {

    /**
     * Activity Action: Start action associated with long press on the recents key.
     * <p>Input: {@link #EXTRA_LONG_PRESS_RELEASE} is set to true if the long press
     * is released
     * <p>Output: Nothing
     * @hide
     */
    public static final String ACTION_RECENTS_LONG_PRESS =
            "evervolv.intent.action.RECENTS_LONG_PRESS";

    /**
     * This field is part of the intent {@link #ACTION_RECENTS_LONG_PRESS}.
     * The type of the extra is a boolean that indicates if the long press
     * is released.
     * @hide
     */
    public static final String EXTRA_RECENTS_LONG_PRESS_RELEASE =
            "evervolv.intent.extra.RECENTS_LONG_PRESS_RELEASE";

    /**
     * Broadcast action: notify the system that the user has performed a gesture on the screen
     * to launch the camera. Broadcast should be protected to receivers holding the
     * {@link Manifest.permission#STATUS_BAR_SERVICE} permission.
     * @hide
     */
    public static final String ACTION_SCREEN_CAMERA_GESTURE =
            "evervolv.intent.action.SCREEN_CAMERA_GESTURE";

    /**
     * Broadcast Action: Update preferences for the power menu dialog.  This is to provide a
     * way for the preferences that need to be enabled/disabled to update because they were
     * toggled elsewhere in the settings (ie profiles, immersive desktop, etc) so we don't have
     * to do constant lookups while we wait for the menu to be created. Getting the values once
     * when necessary is enough.
     * @hide
     */
    public static final String ACTION_UPDATE_POWER_MENU =
            "evervolv.intent.action.UPDATE_POWER_MENU";

    /**
     * Broadcast action: perform any initialization required for LineageHW services.
     * Runs when the service receives the signal the device has booted, but
     * should happen before {@link android.content.Intent#ACTION_BOOT_COMPLETED}.
     *
     * Requires {@link evervolv.platform.Manifest.permission#HARDWARE_ABSTRACTION_ACCESS}.
     * @hide
     */
    public static final String ACTION_INITIALIZE_HARDWARE =
            "evervolv.intent.action.INITIALIZE_HARDWARE";

    /**
     * Broadcast action: notify SystemUI that LiveDisplay service has finished initialization.
     * @hide
     */
    public static final String ACTION_INITIALIZE_LIVEDISPLAY =
            "evervolv.intent.action.INITIALIZE_LIVEDISPLAY";

    /**
     * Broadcast action: lid state changed
     * @hide
     */
    public static final String ACTION_LID_STATE_CHANGED =
            "lineageos.intent.action.LID_STATE_CHANGED";

    /**
     * This field is part of the intent {@link #ACTION_LID_STATE_CHANGED}.
     * Intent extra field for the state of lid/cover
     * @hide
     */
    public static final String EXTRA_LID_STATE =
            "lineageos.intent.extra.LID_STATE";
}
