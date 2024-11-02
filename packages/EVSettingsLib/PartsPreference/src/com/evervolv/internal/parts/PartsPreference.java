/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.evervolv.internal.parts;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;

import evervolv.preference.RemotePreference;

/**
 * A link to a remote preference screen which can be used with a minimum amount
 * of information. Supports summary updates asynchronously.
 */
public class PartsPreference extends RemotePreference {

    private static final String TAG = "PartsPreference";

    private final PartInfo mPart;

    private final Context mContext;

    public PartsPreference(Context context, AttributeSet attrs,
                            int defStyle, int defStyleRes) {
        super(context, attrs, defStyle, defStyleRes);
        mContext = context;
        mPart = PartsList.get(context).getPartInfo(getKey());
        if (mPart == null) {
            throw new RuntimeException("Part not found: " + getKey());
        }

        updatePreference();
        setIntent(mPart.getIntentForActivity());
    }

    public PartsPreference(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, 0);
    }

    public PartsPreference(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    @Override
    public void onRemoteUpdated(Bundle bundle) {
        if (bundle.containsKey(PartsList.EXTRA_PART)) {
            PartInfo update = bundle.getParcelable(PartsList.EXTRA_PART);
            if (update != null) {
                mPart.updateFrom(update);
                updatePreference();
            }
        }
    }

    @Override
    protected String getRemoteKey(Bundle metaData) {
        // remote key is the same as ours
        return getKey();
    }

    private void updatePreference() {
        if (isAvailable() != mPart.isAvailable()) {
            setAvailable(mPart.isAvailable());
        }
        if (isAvailable()) {
            setTitle(mPart.getTitle());
            setSummary(mPart.getSummary());
        }
    }
}
