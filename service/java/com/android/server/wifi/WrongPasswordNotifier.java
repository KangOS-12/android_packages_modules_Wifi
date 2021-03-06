/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.wifi;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.server.wifi.util.NativeUtil;

/**
 * Responsible for notifying user for wrong password errors.
 */
public class WrongPasswordNotifier {
    private static final String TAG = "WrongPasswordNotifier";
    // Number of milliseconds to wait before automatically dismiss the notification.
    private static final long CANCEL_TIMEOUT_MILLISECONDS = 5 * 60 * 1000;

    // Unique ID associated with the notification.
    @VisibleForTesting
    public static final int NOTIFICATION_ID = SystemMessage.NOTE_WIFI_WRONG_PASSWORD;

    // Flag indicating if a wrong password error is detected for the current connection.
    private boolean mWrongPasswordDetected;

    private final WifiContext mContext;
    private final WifiNotificationManager mNotificationManager;
    private final FrameworkFacade mFrameworkFacade;

    public WrongPasswordNotifier(WifiContext context, FrameworkFacade frameworkFacade,
            WifiNotificationManager wifiNotificationManager) {
        mContext = context;
        mFrameworkFacade = frameworkFacade;
        mNotificationManager = wifiNotificationManager;
    }

    /**
     * Invoked when a wrong password error for a Wi-Fi network is detected.
     *
     * @param ssid The SSID of the Wi-Fi network
     */
    public void onWrongPasswordError(String ssid) {
        showNotification(ssid);
        mWrongPasswordDetected = true;
    }

    /**
     * Invoked when attempting a new Wi-Fi network connection.
     */
    public void onNewConnectionAttempt() {
        if (mWrongPasswordDetected) {
            dismissNotification();
            mWrongPasswordDetected = false;
        }
    }

    /**
     * Display wrong password notification for a given Wi-Fi network (specified by its SSID).
     *
     * @param ssid SSID of the Wi-FI network
     */
    private void showNotification(String ssid) {
        String settingsPackage = mFrameworkFacade.getSettingsPackageName(mContext);
        if (settingsPackage == null) return;
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS)
                .setPackage(settingsPackage)
                .putExtra("wifi_start_connect_ssid", NativeUtil.removeEnclosingQuotes(ssid));
        Notification.Builder builder = mFrameworkFacade.makeNotificationBuilder(mContext,
                WifiService.NOTIFICATION_NETWORK_ALERTS)
                .setAutoCancel(true)
                .setTimeoutAfter(CANCEL_TIMEOUT_MILLISECONDS)
                // TODO(zqiu): consider creating a new icon.
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                        com.android.wifi.resources.R.drawable.stat_notify_wifi_in_range))
                .setContentTitle(mContext.getString(
                        com.android.wifi.resources.R.string.wifi_available_title_failed_to_connect))
                .setContentText(ssid)
                .setContentIntent(mFrameworkFacade.getActivity(
                        mContext, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setColor(mContext.getResources().getColor(
                        android.R.color.system_notification_accent_color));
        mNotificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Dismiss the notification that was generated by {@link #showNotification}. The notification
     * might have already been dismissed, either by user or timeout. We'll attempt to dismiss it
     * regardless if it is been dismissed or not, to reduce code complexity.
     */
    private void dismissNotification() {
        // Notification might have already been dismissed, either by user or timeout. It is
        // still okay to cancel it if already dismissed.
        mNotificationManager.cancel(NOTIFICATION_ID);
    }
}
