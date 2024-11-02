/*
 * SPDX-FileCopyrightText: 2015-2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2018 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package evervolv.hardware;

/** @hide */
interface IHardwareService {
    int getSupportedFeatures();
    boolean get(int feature);
    boolean set(int feature, boolean enable);

    int[] getDisplayColorCalibration();
    boolean setDisplayColorCalibration(in int[] rgb);
}
