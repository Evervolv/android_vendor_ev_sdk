/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package evervolv.app;

import android.annotation.SdkConstant;

/**
 * @hide
 * TODO: We need to somehow make these managers accessible via getSystemService
 */
public final class ContextConstants {

    /**
     * @hide
     */
    private ContextConstants() {
        // Empty constructor
    }

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link evervolv.hardware.HardwareManager} to manage the extended
     * hardware features of the device.
     *
     * @see android.content.Context#getSystemService
     * @see evervolv.hardware.HardwareManager
     *
     * @hide
     */
    public static final String HARDWARE_MANAGER = "ev_hardware_manager";

    /**
     * Update power menu (GlobalActions)
     *
     * @hide
     */
    public static final String GLOBAL_ACTIONS_SERVICE = "ev_globalactions";

    /**
     * Manages display color adjustments
     *
     * @hide
     */
    public static final String LIVEDISPLAY_SERVICE = "ev_livedisplay";

    /**
     * Use with {@link android.content.Context#getSystemService} to retrieve a
     * {@link evervolv.health.HealthInterface} to access the Health interface.
     *
     * @see android.content.Context#getSystemService
     * @see lineageos.health.HealthInterface
     *
     * @hide
     */
    public static final String HEALTH_INTERFACE = "ev_health";

    /**
     * Features supported by the Vendor SDK.
     */
    public static class Features {
        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the hardware abstraction
         * framework service utilized by the sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String HARDWARE_ABSTRACTION = "com.evervolv.hardware";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage globalactions
         * service utilized by the lineage sdk and LineageParts.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String GLOBAL_ACTIONS = "com.evervolv.globalactions";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the LiveDisplay service
         * utilized by the lineage sdk.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String LIVEDISPLAY = "com.evervolv.livedisplay";

        /**
         * Feature for {@link PackageManager#getSystemAvailableFeatures} and
         * {@link PackageManager#hasSystemFeature}: The device includes the lineage health
         * service utilized by the lineage sdk and LineageParts.
         */
        @SdkConstant(SdkConstant.SdkConstantType.FEATURE)
        public static final String HEALTH = "com.evervolv.health";
    }
}
