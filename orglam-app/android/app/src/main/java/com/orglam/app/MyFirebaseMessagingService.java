package com.orglam.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Map<String, String> data = remoteMessage.getData();
        String title = data.containsKey("title") ? data.get("title") : "Orglam";
        String body = data.containsKey("body") ? data.get("body") : "";
        String channelId = data.containsKey("channelId") ? data.get("channelId") : "messages";
        String url = data.containsKey("url") ? data.get("url") : "./index.html";

        NotificationManager manager = getSystemService(NotificationManager.class);

        // A to-do reminder is an ALARM, not a notification: it opens a full-screen activity that
        // rings a looping ringtone over the lock screen until it is answered. A notification can
        // only chirp once per push, which is why reminders were missed with the app closed.
        if ("1".equals(data.get("alarm"))) {
            fireAlarm(manager, data, title, body);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH));
        }

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) launchIntent.putExtra("url", url);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, (int) System.currentTimeMillis(), launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            // Without BigTextStyle, Android shows a single truncated line and discards everything
            // after the first newline - which is where the order's item names live.
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void fireAlarm(NotificationManager manager, Map<String, String> data, String title, String body) {
        String channelId = "todo_alarm";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            NotificationChannel ch = new NotificationChannel(channelId, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("To-do reminders that ring until answered");
            // Alarm usage + DND bypass are what let a reminder be heard at night.
            ch.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );
            ch.setBypassDnd(true);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{ 0, 700, 500, 700 });
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(ch);
        }

        Intent alarm = new Intent(this, TodoAlarmActivity.class);
        alarm.putExtra("taskId", data.get("taskId") == null ? "" : data.get("taskId"));
        alarm.putExtra("taskText", data.get("taskText") == null ? title : data.get("taskText"));
        alarm.putExtra("listName", data.get("listName") == null ? "" : data.get("listName"));
        alarm.putExtra("user", data.get("user") == null ? "" : data.get("user"));
        alarm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent full = PendingIntent.getActivity(
            this, 1001, alarm,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Posted as a full-screen-intent notification as well as started directly: Android 10+
        // blocks background activity starts, and the full-screen intent is the sanctioned way for
        // an alarm to take over the screen. The notification is the fallback if the system declines.
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full);

        manager.notify(1001, b.build());

        try {
            startActivity(alarm);
        } catch (Exception ignored) {
            // Background start refused - the full-screen intent above still fires.
        }
    }
}
