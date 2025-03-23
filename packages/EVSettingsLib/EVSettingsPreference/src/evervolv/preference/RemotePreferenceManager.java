/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */
package evervolv.preference;

import static evervolv.preference.RemotePreference.ACTION_REFRESH_PREFERENCE;
import static evervolv.preference.RemotePreference.ACTION_UPDATE_PREFERENCE;
import static evervolv.preference.RemotePreference.EXTRA_KEY;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import evervolv.platform.Manifest;

import java.util.Map;
import java.util.Objects;

/**
 * Manages attaching and detaching of RemotePreferences and optimizes callbacks
 * thru a single receiver on a separate thread.
 *
 * @hide
 */
public class RemotePreferenceManager {

    private static final String TAG = RemotePreferenceManager.class.getSimpleName();

    private static final boolean DEBUG = Log.isLoggable(
            RemotePreference.class.getSimpleName(), Log.VERBOSE);

    private static RemotePreferenceManager sInstance;

    private final Context mContext;
    private final Map<String, Intent> mCache = new ArrayMap<>();
    private final Map<String, OnRemoteUpdateListener> mCallbacks = new ArrayMap<>();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private Handler mHandler;
    private HandlerThread mThread;

    public interface OnRemoteUpdateListener {
        public Intent getReceiverIntent();

        public void onRemoteUpdated(Bundle bundle);
    }

    private RemotePreferenceManager(Context context) {
        mContext = context;
    }

    public synchronized static RemotePreferenceManager get(Context context) {
        if (sInstance == null) {
            sInstance = new RemotePreferenceManager(context);
        }
        return sInstance;
    }

    public void attach(String key, OnRemoteUpdateListener pref) {
        Intent i;
        synchronized (mCache) {
            i = mCache.get(key);
            if (i == null && !mCache.containsKey(key)) {
                i = pref.getReceiverIntent();
                mCache.put(key, i);
            }
        }
        synchronized (mCallbacks) {
            if (i != null) {
                mCallbacks.put(key, pref);
                if (mCallbacks.size() == 1) {
                    mThread = new HandlerThread("RemotePreference");
                    mThread.start();
                    mHandler = new Handler(mThread.getLooper());
                    mContext.registerReceiver(mListener,
                            new IntentFilter(ACTION_REFRESH_PREFERENCE),
                            Manifest.permission.MANAGE_REMOTE_PREFERENCES, mHandler);
                }
                requestUpdate(key);
            }
        }
    }

    public void detach(String key) {
        synchronized (mCallbacks) {
            if (mCallbacks.remove(key) != null && mCallbacks.size() == 0) {
                mContext.unregisterReceiver(mListener);
                if (mThread != null) {
                    mThread.quit();
                }
            }
        }
    }

    private void requestUpdate(String key) {
        synchronized (mCache) {
            Intent i = mCache.get(key);
            if (i == null) {
                return;
            }
            mContext.sendOrderedBroadcastAsUser(i, UserHandle.CURRENT,
                    Manifest.permission.MANAGE_REMOTE_PREFERENCES,
                    mListener, mHandler, Activity.RESULT_OK, null, null);
        }
    }

    private final BroadcastReceiver mListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive: intent=" + Objects.toString(intent));

            if (ACTION_REFRESH_PREFERENCE.equals(intent.getAction())) {
                final String key = intent.getStringExtra(EXTRA_KEY);
                synchronized (mCallbacks) {
                    if (key != null && mCallbacks.containsKey(key)) {
                        requestUpdate(key);
                    }
                }
            } else if (ACTION_UPDATE_PREFERENCE.equals(intent.getAction())) {
                if (getAbortBroadcast()) {
                    Log.e(TAG, "Broadcast aborted, code=" + getResultCode());
                    return;
                }
                final Bundle bundle = getResultExtras(true);
                final String key = bundle.getString(EXTRA_KEY);
                synchronized (mCallbacks) {
                    if (key != null && mCallbacks.containsKey(key)) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (mCallbacks) {
                                    if (mCallbacks.containsKey(key)) {
                                        mCallbacks.get(key).onRemoteUpdated(bundle);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        }
    };
}
