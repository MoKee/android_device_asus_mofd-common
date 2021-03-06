/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
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

package com.cyanogenmod.settings.device;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;
import com.android.internal.util.ArrayUtils;

import mokee.providers.MKSettings;

public class KeyHandler implements DeviceKeyHandler {

    private static final String TAG = KeyHandler.class.getSimpleName();
    private static final int GESTURE_REQUEST = 1;

    // Supported scancodes
    private static final int KEY_GESTURE_C = 249;
    private static final int KEY_GESTURE_E = 250;
    private static final int KEY_GESTURE_S = 251;
    private static final int KEY_GESTURE_V = 252;
    private static final int KEY_GESTURE_W = 253;
    private static final int KEY_GESTURE_Z = 254;

    private static final int GESTURE_WAKELOCK_DURATION = 3000;

    public static final String SMS_DEFAULT_APPLICATION = "sms_default_application";

    private static final int[] sSupportedGestures = new int[] {
        KEY_GESTURE_C,
        KEY_GESTURE_E,
        KEY_GESTURE_S,
        KEY_GESTURE_V,
        KEY_GESTURE_W,
        KEY_GESTURE_Z
    };

    private final Context mContext;
    private final PowerManager mPowerManager;
    private EventHandler mEventHandler;
    private SensorManager mSensorManager;
    private CameraManager mCameraManager;
    private String mRearCameraId;
    private boolean mTorchEnabled;
    private Sensor mProximitySensor;
    private Vibrator mVibrator;
    WakeLock mProximityWakeLock;
    WakeLock mGestureWakeLock;
    private int mProximityTimeOut;
    private boolean mProximityWakeSupported;

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mEventHandler = new EventHandler();
        mGestureWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GestureWakeLock");

        final Resources resources = mContext.getResources();
        mProximityTimeOut = resources.getInteger(
                org.mokee.platform.internal.R.integer.config_proximityCheckTimeout);
        mProximityWakeSupported = resources.getBoolean(
                org.mokee.platform.internal.R.bool.config_proximityCheckOnWake);

        if (mProximityWakeSupported) {
            mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mProximityWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "ProximityWakeLock");
        }

        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            mVibrator = null;
        }

        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerTorchCallback(new MyTorchCallback(), mEventHandler);
    }

    private class MyTorchCallback extends CameraManager.TorchCallback {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = enabled;
        }

        @Override
        public void onTorchModeUnavailable(String cameraId) {
            if (!cameraId.equals(mRearCameraId))
                return;
            mTorchEnabled = false;
        }
    }

    private String getRearCameraId() {
        if (mRearCameraId == null) {
            try {
                for (final String cameraId : mCameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics =
                            mCameraManager.getCameraCharacteristics(cameraId);
                    int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                        mRearCameraId = cameraId;
                        break;
                    }
                }
            } catch (CameraAccessException e) {
                // Ignore
            }
        }
        return mRearCameraId;
    }

    private class EventHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.arg1) {
            case KEY_GESTURE_C:
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                Intent c_intent = new Intent(mokee.content.Intent.ACTION_SCREEN_CAMERA_GESTURE);
                mContext.sendBroadcast(c_intent, Manifest.permission.STATUS_BAR_SERVICE);
                doHapticFeedback();
                break;

            case KEY_GESTURE_E:
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "wakeup-gesture");
                Intent e_intent = new Intent(Intent.ACTION_MAIN, null);
                e_intent.addCategory(Intent.CATEGORY_APP_EMAIL);
                startActivitySafely(e_intent);
                doHapticFeedback();
                break;

            case KEY_GESTURE_S:

                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "wakeup-gesture");
                String defaultApplication = Settings.Secure.getString(mContext.getContentResolver(),
                    SMS_DEFAULT_APPLICATION);
                PackageManager pm = mContext.getPackageManager();
                Intent s_intent = pm.getLaunchIntentForPackage(defaultApplication );
                if (s_intent != null) {
                    startActivitySafely(s_intent);
                    doHapticFeedback();
                }
                break;

            case KEY_GESTURE_V:
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "wakeup-gesture");
                Intent v_intent = new Intent(Intent.ACTION_DIAL, null);
                startActivitySafely(v_intent);
                doHapticFeedback();
                break;

            case KEY_GESTURE_W:
                mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), "wakeup-gesture");
                Intent w_intent = new Intent(Intent.ACTION_WEB_SEARCH, null);
                startActivitySafely(w_intent);
                doHapticFeedback();
                break;

            case KEY_GESTURE_Z: {
                String rearCameraId = getRearCameraId();
                if (rearCameraId != null) {
                    mGestureWakeLock.acquire(GESTURE_WAKELOCK_DURATION);
                    try {
                        mCameraManager.setTorchMode(rearCameraId, !mTorchEnabled);
                        mTorchEnabled = !mTorchEnabled;
                    } catch (CameraAccessException e) {
                        // Ignore
                    }
                    doHapticFeedback();
                }
                break;
            }
            }
        }
    }

    public boolean handleKeyEvent(KeyEvent event) {
        boolean isKeySupported = ArrayUtils.contains(sSupportedGestures, event.getScanCode());
        if (!isKeySupported) {
            return false;
        }

        if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
            Message msg = getMessageForKeyEvent(event.getScanCode());
            boolean defaultProximity = mContext.getResources().getBoolean(
                org.mokee.platform.internal.R.bool.config_proximityCheckOnWakeEnabledByDefault);
            boolean proximityWakeCheckEnabled = MKSettings.System.getInt(mContext.getContentResolver(),
                    MKSettings.System.PROXIMITY_ON_WAKE, defaultProximity ? 1 : 0) == 1;
            if (mProximityWakeSupported && proximityWakeCheckEnabled && mProximitySensor != null) {
                mEventHandler.sendMessageDelayed(msg, mProximityTimeOut);
                processEvent(event.getScanCode());
            } else {
                mEventHandler.sendMessage(msg);
            }
        }
        return true;
    }

    private Message getMessageForKeyEvent(int scancode) {
        Message msg = mEventHandler.obtainMessage(GESTURE_REQUEST);
        msg.arg1 = scancode;
        return msg;
    }

    private void processEvent(final int scancode) {
        mProximityWakeLock.acquire();
        mSensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                mProximityWakeLock.release();
                mSensorManager.unregisterListener(this);
                if (!mEventHandler.hasMessages(GESTURE_REQUEST)) {
                    // The sensor took to long, ignoring.
                    return;
                }
                mEventHandler.removeMessages(GESTURE_REQUEST);
                if (event.values[0] == mProximitySensor.getMaximumRange()) {
                    Message msg = getMessageForKeyEvent(scancode);
                    mEventHandler.sendMessage(msg);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        }, mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    private void startActivitySafely(Intent intent) {
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            UserHandle user = new UserHandle(UserHandle.USER_CURRENT);
            mContext.startActivityAsUser(intent, null, user);
        } catch (ActivityNotFoundException e) {
            // Ignore
        }
    }

    private void doHapticFeedback() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(
                Context.AUDIO_SERVICE);

        if (mVibrator == null) {
            return;
        }

        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            boolean enabled = MKSettings.System.getInt(mContext.getContentResolver(),
                    MKSettings.System.TOUCHSCREEN_GESTURE_HAPTIC_FEEDBACK, 1) != 0;
            if (enabled) {
                mVibrator.vibrate(50);
            }
        }
    }
}
