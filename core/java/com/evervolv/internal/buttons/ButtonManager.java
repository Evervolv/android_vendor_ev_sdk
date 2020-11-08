/**
 * Copyright (C) 2018,2020 The LineageOS Project
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

package com.evervolv.internal.buttons;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionLegacyHelper;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import evervolv.provider.EVSettings;

import java.util.List;

public final class ButtonManager {
    private final String TAG = "ButtonManager";
    private final boolean DEBUG = false;

    // Default duration for button lights.
    private static final int DEFAULT_BUTTON_ON_DURATION = 5 * 1000;

    private static final int MSG_DISPATCH_VOLKEY_WITH_WAKELOCK = 1;

    private final Context mContext;
    private final ButtonHandler mHandler;
    private final MediaSessionManager mMediaSessionManager;

    private boolean mIsLongPress = false;

    private boolean mVolBtnMusicControls = false;

    private int mButtonTimeout;
    private float mButtonBrightness;
    private float mButtonBrightnessDefault;

    private class ButtonHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DISPATCH_VOLKEY_WITH_WAKELOCK:
                    KeyEvent ev = (KeyEvent) msg.obj;
                    mIsLongPress = true;
                    if (DEBUG) {
                        Slog.d(TAG, "Dispatching key to audio service");
                    }
                    onSkipTrackEvent(ev);
                    break;
            }
        }
    }

    public ButtonManager(Context context) {
        mContext = context;
        mHandler = new ButtonHandler();
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);

        mButtonBrightnessDefault = mContext.getResources().getFloat(
                com.evervolv.platform.internal.R.dimen.config_buttonBrightnessSettingDefaultFloat);

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe();
    }

    public boolean handleVolumeKey(KeyEvent event, boolean isInteractive) {
        final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        final int keyCode = event.getKeyCode();

        if (isInteractive) {
            // nothing to do here for now
            if (DEBUG) {
                Slog.d(TAG, "Skipping because interactive");
            }
            return false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (!mVolBtnMusicControls) {
                    return false;
                }

                if (down) {
                    mIsLongPress = false;
                    // queue skip event
                    int newKeyCode = (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                            ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                            : KeyEvent.KEYCODE_MEDIA_NEXT);

                    KeyEvent newEvent = new KeyEvent(event.getDownTime(), event.getEventTime(),
                            event.getAction(), newKeyCode, 0);
                    if (DEBUG) {
                        Slog.d(TAG, "Queueing media " +
                                (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? "previous" : "next") +
                                " event " + newEvent);
                    }
                    Message msg = mHandler.obtainMessage(MSG_DISPATCH_VOLKEY_WITH_WAKELOCK,
                            newEvent);
                    msg.setAsynchronous(true);
                    mHandler.sendMessageDelayed(msg, ViewConfiguration.getLongPressTimeout());
                } else {
                    // cancel skip event
                    mHandler.removeMessages(MSG_DISPATCH_VOLKEY_WITH_WAKELOCK);

                    if (mIsLongPress) {
                        // if key was long pressed, media next/prev action has been performed,
                        // so don't change volume
                        break;
                    }
                    // sendVolumeKeyEvent will only change the volume on ACTION_DOWN,
                    // so fake the ACTION_DOWN event.
                    KeyEvent newEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
                    MediaSessionLegacyHelper.getHelper(mContext).sendVolumeKeyEvent(newEvent,
                            AudioManager.USE_DEFAULT_STREAM_TYPE, true);
                }
                break;
            default:
                // key unhandled
                return false;
        }
        return true;
    }

    private void triggerKeyEvents(KeyEvent evDown, MediaController controller) {
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);
        mHandler.post(() -> controller.dispatchMediaButtonEvent(evDown));
        mHandler.postDelayed(() -> controller.dispatchMediaButtonEvent(evUp), 20);
    }

    public void onSkipTrackEvent(KeyEvent ev) {
        if (mMediaSessionManager != null) {
            final List<MediaController> sessions = mMediaSessionManager.getActiveSessionsForUser(
                    null, UserHandle.ALL);
            for (MediaController mediaController : sessions) {
                if (PlaybackState.STATE_PLAYING ==
                        getMediaControllerPlaybackState(mediaController)) {
                    triggerKeyEvents(ev, mediaController);
                    break;
                }
            }
        }
    }

    private int getMediaControllerPlaybackState(MediaController controller) {
        if (controller != null) {
            final PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getState();
            }
        }
        return PlaybackState.STATE_NONE;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(EVSettings.System.getUriFor(
                    EVSettings.System.VOLBTN_MUSIC_CONTROLS),
                            false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(EVSettings.System.getUriFor(
                    EVSettings.System.BUTTON_BRIGHTNESS),
                            false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(EVSettings.System.getUriFor(
                    EVSettings.System.BUTTON_BACKLIGHT_TIMEOUT),
                            false, this, UserHandle.USER_ALL);

            update();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        private void update() {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            mVolBtnMusicControls = EVSettings.System.getIntForUser(
                    resolver, EVSettings.System.VOLBTN_MUSIC_CONTROLS, 1,
                    UserHandle.USER_CURRENT) == 1;

            if (DEBUG) {
                Slog.d(TAG, "music controls enabled = " + mVolBtnMusicControls);
            }

            mButtonTimeout = EVSettings.System.getIntForUser(
                    resolver, EVSettings.System.BUTTON_BACKLIGHT_TIMEOUT,
                    DEFAULT_BUTTON_ON_DURATION, UserHandle.USER_CURRENT);

            mButtonBrightness = EVSettings.System.getFloatForUser(
                    resolver, EVSettings.System.BUTTON_BRIGHTNESS, 
                    mButtonBrightnessDefault, UserHandle.USER_CURRENT);
        }
    }

    public float getButtonBrightnessDefault() {
        return mButtonBrightnessDefault;
    }

    public float getButtonBrightness() {
        return mButtonBrightness;
    }

    public int getButtonTimeout() {
        return mButtonTimeout;
    }
}
