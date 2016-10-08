/*
 * Copyright (C) 2016 The CyanogenMod Project
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
package com.evervolv.internal.parts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.AttributeSet;

import evervolv.preference.SelfRemovingPreference;

import static com.evervolv.internal.parts.PartsList.ACTION_PART_CHANGED;
import static com.evervolv.internal.parts.PartsList.EXTRA_PART;
import static com.evervolv.internal.parts.PartsList.EXTRA_PART_KEY;

public class PartsPreference extends SelfRemovingPreference {

    private static final String TAG = "PartsPreference";

    private final PartInfo mPart;

    public PartsPreference(Context context, AttributeSet attrs) {
        super(context, attrs, com.android.internal.R.attr.preferenceScreenStyle);

        mPart = PartsList.getPartInfo(context, getKey());
        if (mPart == null) {
            throw new RuntimeException("Part not found: " + getKey());
        }

        if (!mPart.isAvailable()) {
            setAvailable(false);
        }

        setIntent(mPart.getIntentForActivity());
        update();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        getContext().registerReceiver(mPartChangedReceiver, new IntentFilter(ACTION_PART_CHANGED));
    }

    @Override
    public void onDetached() {
        super.onDetached();
        getContext().unregisterReceiver(mPartChangedReceiver);
    }

    private void update() {
        setTitle(mPart.getTitle());
        setSummary((CharSequence) mPart.getSummary());
    }

    private final BroadcastReceiver mPartChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_PART_CHANGED.equals(intent.getAction()) &&
                    mPart.getName().equals(intent.getStringExtra(EXTRA_PART_KEY))) {
                mPart.updateFrom((PartInfo) intent.getParcelableExtra(EXTRA_PART));
                update();
            }
        }
    };
}
