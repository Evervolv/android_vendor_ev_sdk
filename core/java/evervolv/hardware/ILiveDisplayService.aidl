/**
 * Copyright (c) 2016, The CyanogenMod Project
 *               2021 The LineageOS Project
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

package evervolv.hardware;

import evervolv.hardware.DisplayMode;
import evervolv.hardware.HSIC;
import evervolv.hardware.LiveDisplayConfig;

/** @hide */
interface ILiveDisplayService {
    LiveDisplayConfig getConfig();

    int getMode();
    boolean setMode(int mode);

    float[] getColorAdjustment();
    boolean setColorAdjustment(in float[] adj);

    int getDayColorTemperature();
    boolean setDayColorTemperature(int temperature);

    int getNightColorTemperature();
    boolean setNightColorTemperature(int temperature);

    int getColorTemperature();

    HSIC getPictureAdjustment();
    HSIC getDefaultPictureAdjustment();
    boolean setPictureAdjustment(in HSIC adj);
    boolean isNight();

    boolean getFeature(int feature);
    boolean setFeature(int feature, boolean enable);

    int[] getDisplayColorCalibration();
    int getDisplayColorCalibrationMin();
    int getDisplayColorCalibrationMax();
    boolean setDisplayColorCalibration(in int[] rgb);

    DisplayMode[] getDisplayModes();
    DisplayMode getCurrentDisplayMode();
    DisplayMode getDefaultDisplayMode();
    boolean setDisplayMode(in DisplayMode mode, boolean makeDefault);
}
