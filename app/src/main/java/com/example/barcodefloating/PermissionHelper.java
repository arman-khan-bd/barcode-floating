package com.example.barcodefloating;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    public static final int REQ_CODE_CAMERA = 101;
    public static final int REQ_CODE_OVERLAY = 102;
    public static final int REQ_CODE_NOTIFICATIONS = 103;

    /**
     * Checks if camera permission is granted.
     * On Android 5.0 - 5.1 (API 21-22), permissions are granted at install time.
     */
    public static boolean hasCameraPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Requests camera permission if running on API 23+.
     */
    public static void requestCameraPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CAMERA},
                    REQ_CODE_CAMERA);
        }
    }

    /**
     * Checks if notification permission is granted (Android 13+ / API 33+).
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Requests notification permission on Android 13+.
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_CODE_NOTIFICATIONS);
        }
    }

    /**
     * Checks if the app can draw overlays over other applications.
     * On Android 5.0 - 5.1 (API 21-22), SYSTEM_ALERT_WINDOW is granted at install time.
     */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    /**
     * Navigates user to system settings to grant Overlay (draw over other apps) permission.
     */
    public static void requestOverlayPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQ_CODE_OVERLAY);
        }
    }

    /**
     * Checks whether the BarcodePasteAccessibilityService is currently enabled in Android Accessibility Settings.
     */
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        int accessibilityEnabled = 0;
        final String expectedService = context.getPackageName() + "/" + serviceClass.getName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {
        }

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString(settingValue);
                while (colonSplitter.hasNext()) {
                    String serviceName = colonSplitter.next();
                    if (serviceName.equalsIgnoreCase(expectedService)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Opens Android Accessibility Settings page so user can toggle our service ON.
     */
    public static void openAccessibilitySettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
