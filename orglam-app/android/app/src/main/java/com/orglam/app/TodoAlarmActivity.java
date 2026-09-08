package com.orglam.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A real alarm, not a notification: shows over the lock screen with the screen off, plays a LOOPING
 * ringtone on the ALARM stream (so it is heard under Do Not Disturb and at alarm volume, unlike a
 * notification sound), and vibrates until the user answers. Answering posts back to the worker so
 * the server stops re-pushing the reminder.
 */
public class TodoAlarmActivity extends Activity {

    private static final String WORKER = "https://dawn-king-6cc3.orglam-service.workers.dev";

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private String taskId = "";
    private String user = "";
    private String tone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over the lock screen and turn the screen on. On Android 8.1+ these are the supported
        // calls; the window flags cover older versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "orglam:todoAlarm"
            );
            wakeLock.acquire(10 * 60 * 1000L);
        }

        Intent i = getIntent();
        taskId = i.getStringExtra("taskId") == null ? "" : i.getStringExtra("taskId");
        user = i.getStringExtra("user") == null ? "" : i.getStringExtra("user");
        tone = i.getStringExtra("tone") == null ? "" : i.getStringExtra("tone");
        String taskText = i.getStringExtra("taskText") == null ? "Reminder" : i.getStringExtra("taskText");
        String listName = i.getStringExtra("listName") == null ? "" : i.getStringExtra("listName");

        setContentView(buildUi(taskText, listName));
        startRinging();
    }

    private View buildUi(String taskText, String listName) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#1a2219"));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);

        TextView bell = new TextView(this);
        bell.setText("\uD83D\uDD14");
        bell.setTextSize(52);
        bell.setGravity(Gravity.CENTER);
        root.addView(bell);

        TextView heading = new TextView(this);
        heading.setText("Reminder");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(12), 0, 0);
        root.addView(heading);

        TextView task = new TextView(this);
        task.setText(taskText);
        task.setTextColor(Color.WHITE);
        task.setTextSize(19);
        task.setGravity(Gravity.CENTER);
        task.setPadding(0, dp(10), 0, 0);
        root.addView(task);

        if (!listName.isEmpty()) {
            TextView list = new TextView(this);
            list.setText(listName);
            list.setTextColor(Color.parseColor("#a9b79f"));
            list.setTextSize(14);
            list.setGravity(Gravity.CENTER);
            list.setPadding(0, dp(4), 0, 0);
            root.addView(list);
        }

        Button done = new Button(this);
        done.setText("Mark done");
        done.setAllCaps(false);
        done.setTextColor(Color.WHITE);
        done.setBackgroundColor(Color.parseColor("#5c6650"));
        done.setTextSize(17);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        dlp.topMargin = dp(28);
        done.setLayoutParams(dlp);
        done.setOnClickListener(v -> answer(true, 0));
        root.addView(done);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        row.setLayoutParams(rlp);

        Button snooze = new Button(this);
        snooze.setText("Snooze 10 min");
        snooze.setAllCaps(false);
        snooze.setTextColor(Color.WHITE);
        snooze.setBackgroundColor(Color.parseColor("#3a4836"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        slp.rightMargin = dp(6);
        snooze.setLayoutParams(slp);
        snooze.setOnClickListener(v -> answer(false, 10));
        row.addView(snooze);

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setAllCaps(false);
        stop.setTextColor(Color.WHITE);
        stop.setBackgroundColor(Color.parseColor("#3a4836"));
        stop.setLayoutParams(new LinearLayout.LayoutParams(0, dp(50), 1f));
        stop.setOnClickListener(v -> answer(false, 0));
        row.addView(stop);

        root.addView(row);
        return root;
    }

    private void startRinging() {
        try {
            // The tone the user picked in the app, bundled as res/raw/alarm_<name>.wav so the alarm
            // sounds identical whether the app was open or closed. Falls back to the phone's own
            // alarm ringtone if the name is unknown or the file is missing.
            Uri tone = toneUri();
            if (tone == null) tone = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
            if (tone == null) tone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            player = new MediaPlayer();
            player.setDataSource(this, tone);
            // The ALARM usage is the whole point: it plays at alarm volume and is exempt from Do Not
            // Disturb, which a notification sound is not.
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) { }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = { 0, 700, 500, 700, 500 };
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) { }

        // Safety valve: an alarm nobody answers must not ring forever and flatten the battery.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing()) answer(false, 0);
        }, 10 * 60 * 1000L);
    }

    /** Resolves the chosen tone name to a bundled raw resource, or null if there isn't one. */
    private Uri toneUri() {
        if (tone == null || tone.isEmpty()) return null;
        String safe = tone.replaceAll("[^a-z0-9_]", "");
        if (safe.isEmpty()) return null;
        int id = getResources().getIdentifier("alarm_" + safe, "raw", getPackageName());
        if (id == 0) return null;
        return Uri.parse("android.resource://" + getPackageName() + "/" + id);
    }

    private void stopRinging() {
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) { }
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) { }
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
    }

    /** Tells the server the alarm was answered, so it stops re-pushing this reminder. */
    private void answer(boolean done, int snoozeMinutes) {
        stopRinging();
        final String body = buildAckJson(done, snoozeMinutes);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(WORKER + "/todos/ack").openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) { }
        }).start();
        finish();
    }

    private String buildAckJson(boolean done, int snoozeMinutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"user\":\"").append(esc(user)).append("\",\"taskId\":\"").append(esc(taskId)).append("\"");
        sb.append(",\"done\":").append(done ? "true" : "false");
        if (snoozeMinutes > 0) {
            long when = System.currentTimeMillis() + snoozeMinutes * 60_000L;
            // ISO 8601 UTC - the same shape the web app stores, so both agree on the new time.
            java.text.SimpleDateFormat f =
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            sb.append(",\"remindAt\":\"").append(f.format(new java.util.Date(when))).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        // The alarm must be answered, not dismissed - that is what separates it from a notification.
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        super.onDestroy();
    }
}
