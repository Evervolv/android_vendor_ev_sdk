/*
 * Copyright (C) 2015 The Android Open Source Project
 *               2016 The CyanogenMod Project
 *               2017,2019,2021 The LineageOS Project
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
package com.evervolv.settingslib.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.PreferenceDialogFragmentCompat;

public class CustomDialogPreferenceExt extends DialogPreference {

    private CustomPreferenceDialogFragment mFragment;
    private DialogInterface.OnShowListener mOnShowListener;

    public CustomDialogPreferenceExt(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CustomDialogPreferenceExt(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CustomDialogPreferenceExt(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomDialogPreferenceExt(Context context) {
        super(context);
    }

    public boolean isDialogOpen() {
        return getDialog() != null && getDialog().isShowing();
    }

    public Dialog getDialog() {
        return mFragment != null ? mFragment.getDialog() : null;
    }

    public void setOnShowListener(DialogInterface.OnShowListener listner) {
        mOnShowListener = listner;
    }

    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
    }

    protected void onDialogClosed(boolean positiveResult) {
    }

    protected void onClick(DialogInterface dialog, int which) {
    }

    protected void onBindDialogView(View view) {
    }

    private void setFragment(CustomPreferenceDialogFragment fragment) {
        mFragment = fragment;
    }

    private DialogInterface.OnShowListener getOnShowListener() {
        return mOnShowListener;
    }

    protected void onStart() {
    }

    protected void onStop() {
    }

    protected void onPause() {
    }

    protected void onResume() {
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return null;
    }

    protected View onCreateDialogView(Context context) {
        return null;
    }

    protected boolean onDismissDialog(DialogInterface dialog, int which) {
        return true;
    }

    public static class CustomPreferenceDialogFragment extends PreferenceDialogFragmentCompat {

        public static CustomPreferenceDialogFragment newInstance(String key) {
            final CustomPreferenceDialogFragment fragment = new CustomPreferenceDialogFragment();
            final Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        private class OnDismissListener implements View.OnClickListener {
            private final int mWhich;
            private final DialogInterface mDialog;

            public OnDismissListener(DialogInterface dialog, int which) {
                mWhich = which;
                mDialog = dialog;
            }

            @Override
            public void onClick(View view) {
                CustomPreferenceDialogFragment.this.onClick(mDialog, mWhich);
                if (getCustomizablePreference().onDismissDialog(mDialog, mWhich)) {
                    mDialog.dismiss();
                }
            }
        }

        private CustomDialogPreferenceExt getCustomizablePreference() {
            return (CustomDialogPreferenceExt) getPreference();
        }

        @Override
        protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
            super.onPrepareDialogBuilder(builder);
            getCustomizablePreference().setFragment(this);
            getCustomizablePreference().onPrepareDialogBuilder(builder, this);
        }

        @Override
        public void onDialogClosed(boolean positiveResult) {
            getCustomizablePreference().onDialogClosed(positiveResult);
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);
            getCustomizablePreference().onBindDialogView(view);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            getCustomizablePreference().setFragment(this);
            final Dialog sub = getCustomizablePreference().onCreateDialog(savedInstanceState);
            if (sub == null) {
                final Dialog dialog = super.onCreateDialog(savedInstanceState);
                dialog.setOnShowListener(getCustomizablePreference().getOnShowListener());
                return dialog;
            }
            return sub;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            super.onClick(dialog, which);
            getCustomizablePreference().onClick(dialog, which);
        }

        @Override
        public void onStart() {
            super.onStart();
            if (getDialog() instanceof AlertDialog) {
                AlertDialog a = (AlertDialog)getDialog();
                if (a.getButton(Dialog.BUTTON_NEUTRAL) != null) {
                    a.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener(
                            new OnDismissListener(a, Dialog.BUTTON_NEUTRAL));
                }
                if (a.getButton(Dialog.BUTTON_POSITIVE) != null) {
                    a.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(
                            new OnDismissListener(a, Dialog.BUTTON_POSITIVE));
                }
                if (a.getButton(Dialog.BUTTON_NEGATIVE) != null) {
                    a.getButton(Dialog.BUTTON_NEGATIVE).setOnClickListener(
                            new OnDismissListener(a, Dialog.BUTTON_NEGATIVE));
                }
            }
            getCustomizablePreference().onStart();
        }

        @Override
        public void onStop() {
            super.onStop();
            getCustomizablePreference().onStop();
        }

        @Override
        public void onPause() {
            super.onPause();
            getCustomizablePreference().onPause();
        }

        @Override
        public void onResume() {
            super.onResume();
            getCustomizablePreference().onResume();
        }
    }
}
