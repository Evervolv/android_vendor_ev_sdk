/*
 * Copyright (C) 2016 The CyanogenMod project
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

package com.evervolv.internal.preference.deviceinfo;

import android.content.Context;
import android.util.AttributeSet;

import evervolv.os.Build;
import evervolv.preference.SelfRemovingPreference;

import com.evervolv.platform.internal.R;

public class PlatformVersionPreference extends SelfRemovingPreference {

    public PlatformVersionPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public PlatformVersionPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlatformVersionPreference(Context context) {
        super(context);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        setTitle(R.string.platform_version);

        StringBuilder builder = new StringBuilder();
        builder.append(Build.getNameForSDKInt(Build.EVERVOLV_VERSION.SDK_INT))
                .append(" (" + Build.EVERVOLV_VERSION.SDK_INT + ")");
        setSummary(builder.toString());
    }
}
