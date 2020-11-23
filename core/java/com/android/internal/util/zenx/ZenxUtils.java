/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.zenx;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.fingerprint.FingerprintManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.net.NetworkInfo;
import android.net.ConnectivityManager;
import android.os.SystemProperties;
import android.text.format.Time;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.provider.Settings;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.os.AsyncTask;
import android.app.IActivityManager;
import android.app.ActivityManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.graphics.Color;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.os.ServiceManager;
import android.app.UiModeManager;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ZenxUtils {

    public static final String INTENT_SCREENSHOT = "action_take_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_take_region_screenshot";

    private static OverlayManager mOverlayService;

    // Check if device is connected to Wi-Fi
    public static boolean isWiFiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifi.isConnected();
    }

    // Check to see if device is WiFi only
    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

	// Returns today's passed time in Millisecond
    public static long getTodayMillis() {
        final long passedMillis;
        Time time = new Time();
        time.set(System.currentTimeMillis());
        passedMillis = ((time.hour * 60 * 60) + (time.minute * 60) + time.second) * 1000;
        return passedMillis;
    }

	// Check if device is connected to the internet
    public static boolean isConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo mobile = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return wifi.isConnected() || mobile.isConnected();
    }

    public static String batteryTemperature(Context context, Boolean ForC) {
        Intent intent = context.registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        float  temp = ((float) (intent != null ? intent.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE, 0) : 0)) / 10;
        // Round up to nearest number
        int c = (int) ((temp) + 0.5f);
        float n = temp + 0.5f;
        // Use boolean to determine celsius or fahrenheit
        return String.valueOf((n - c) % 2 == 0 ? (int) temp :
                ForC ? c * 9/5 + 32:c);
    }

    // Method to detect countries that use Fahrenheit
    public static boolean mccCheck(Context context) {
        // MCC's belonging to countries that use Fahrenheit
        String[] mcc = {"364", "552", "702", "346", "550", "376", "330",
                "310", "311", "312", "551"};

        TelephonyManager tel = (TelephonyManager) context.getSystemService(
                Context.TELEPHONY_SERVICE);
        String networkOperator = tel.getNetworkOperator();

        // Check the array to determine celsius or fahrenheit.
        // Default to celsius if can't access MCC
        return !TextUtils.isEmpty(networkOperator) && Arrays.asList(mcc).contains(
                networkOperator.substring(0, /*Filter only 3 digits*/ 3));
    }

    // Check to see if a package is installed
    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    // Check to see if device supports the Fingerprint scanner
    public static boolean hasFingerprintSupport(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected());
    }

    // Check to see if device not only supports the Fingerprint scanner but also if is enrolled
    public static boolean hasFingerprintEnrolled(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints());
    }

    // Check to see if device has a camera
    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    // Check to see if device supports NFC
    public static boolean hasNFC(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    // Check to see if device supports Wifi
    public static boolean hasWiFi(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    // Check to see if device supports Bluetooth
    public static boolean hasBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    // Check to see if device supports an alterative ambient display package
    public static boolean hasAltAmbientDisplay(Context context) {
        return context.getResources().getBoolean(com.android.internal.R.bool.config_alt_ambient_display);
    }

    // Check to see if device supports A/B (seamless) system updates
    public static boolean isABdevice(Context context) {
        return SystemProperties.getBoolean("ro.build.ab_update", false);
    }

    // Check for Chinese language
    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    // Method to turn off the screen
    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;

        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }
    }

    public static void takeScreenshot(boolean full) {
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        try {
            wm.sendCustomAction(new Intent(full? INTENT_SCREENSHOT : INTENT_REGION_SCREENSHOT));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static boolean deviceHasCompass(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    // Check for lockscreen accent color hour
    public static boolean useLockscreenClockHourAccentColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
          Settings.System.LOCKSCREEN_ACCENT_COLOR_HOUR, 0) == 1;
    }

    // Check for lockscreen accent color minute
    public static boolean useLockscreenClockMinuteAccentColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
          Settings.System.LOCKSCREEN_ACCENT_COLOR_MINUTE, 0) == 1;
    }

    // Check for lockscreen accent color custom clocks
    public static boolean useLockscreenCustomClockAccentColor(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
          Settings.System.LOCKSCREEN_ACCENT_COLOR_CUSTOM, 0) == 1;
    }

        /**
     * @hide
     */
    public static final String SYSTEMUI_PACKAGE_NAME = "com.android.systemui";

    /**
     * @hide
     */
    public static final String ACTION_DISMISS_KEYGUARD = SYSTEMUI_PACKAGE_NAME +".ACTION_DISMISS_KEYGUARD";

    /**
     * @hide
     */
    public static final String DISMISS_KEYGUARD_EXTRA_INTENT = "launch";

    /**
     * @hide
     */
    public static void launchKeyguardDismissIntent(Context context, UserHandle user, Intent launchIntent) {
        Intent keyguardIntent = new Intent(ACTION_DISMISS_KEYGUARD);
        keyguardIntent.setPackage(SYSTEMUI_PACKAGE_NAME);
        keyguardIntent.putExtra(DISMISS_KEYGUARD_EXTRA_INTENT, launchIntent);
        context.sendBroadcastAsUser(keyguardIntent, user);
    }

    public static int getBlendColorForPercent(int fullColor, int emptyColor, boolean reversed,
        int percentage) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(emptyColor, empty);
        int emptyAlpha = Color.alpha(emptyColor);
        float blendFactor = percentage/100f;
        if (reversed) {
        if (empty[0] < full[0]) {
        empty[0] += 360f;
        }
        newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
        if (empty[0] > full[0]) {
        full[0] += 360f;
        }
        newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
        newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
        newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }

     public static void restartSystemUi(Context context) {
        new RestartSystemUiTask(context).execute();
    }

    public static void showSystemUiRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("SystemUI restart required")
                .setMessage("For all changes to take effect, a SystemUI restart is required. Restart SystemUI now?")
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSystemUi(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class RestartSystemUiTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;


        public RestartSystemUiTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.systemui".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
                //Class ActivityManagerNative = Class.forName("android.app.ActivityManagerNative");
                //Method getDefault = ActivityManagerNative.getDeclaredMethod("getDefault", null);
                //Object amn = getDefault.invoke(null, null);
                //Method killApplicationProcess = amn.getClass().getDeclaredMethod("killApplicationProcess", String.class, int.class);
                //mContext.stopService(new Intent().setComponent(new ComponentName("com.android.systemui", "com.android.systemui.SystemUIService")));
                //am.killBackgroundProcesses("com.android.systemui");
                //for (ActivityManager.RunningAppProcessInfo app : am.getRunningAppProcesses()) {
                //    if ("com.android.systemui".equals(app.processName)) {
                //        killApplicationProcess.invoke(amn, app.processName, app.uid);
                //        break;
                //    }
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isThemeEnabled(String packageName) {
        if (mOverlayService == null) {
            mOverlayService = new OverlayManager();
        }
        try {
            ArrayList<OverlayInfo> infos = new ArrayList<OverlayInfo>();
            infos.addAll(mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId()));
            infos.addAll(mOverlayService.getOverlayInfosForTarget("com.android.systemui",
                    UserHandle.myUserId()));
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    // Method to detect whether the system dark theme is enabled or not
    public static boolean isDarkTheme(Context context) {
        UiModeManager mUiModeManager =
                context.getSystemService(UiModeManager.class);
        if (mUiModeManager == null) return false;
        int mode = mUiModeManager.getNightMode();
        return (mode == UiModeManager.MODE_NIGHT_YES);
    }
}
