package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the launcher can be given notification access, which is what Android asks of an app that reads
 * other apps' media sessions (NowPlaying). It does nothing with the notifications themselves. Granted over ADB with
 * `cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaListener`.
 */
public class MediaListener extends NotificationListenerService {
}
