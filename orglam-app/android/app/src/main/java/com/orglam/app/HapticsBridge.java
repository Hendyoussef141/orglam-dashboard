package com.orglam.app;   // <-- must match your MainActivity's package

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.JavascriptInterface;

public class HapticsBridge {
    private final Vibrator vibrator;

    public HapticsBridge(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm =
                (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = vm != null ? vm.getDefaultVibrator() : null;
        } else {
            this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /** Single pulse, milliseconds. Called for taps, detents, edges. */
    @JavascriptInterface
    public void vibrate(int ms) {
        if (vibrator == null || !vibrator.hasVibrator() || ms <= 0) return;
        // Cap defensively - the UI never asks for more than ~35ms, and a stuck value
        // buzzing for seconds would be far worse than no haptics at all.
        if (ms > 200) ms = 200;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    /**
     * Comma-separated pattern, e.g. "9,34,16" = buzz 9ms, pause 34ms, buzz 16ms.
     * Used for the double-pulse on toggles and the back gesture.
     */
    @JavascriptInterface
    public void vibratePattern(String csv) {
        if (vibrator == null || !vibrator.hasVibrator() || csv == null) return;
        String[] parts = csv.split(",");
        long[] timings = new long[parts.length + 1];
        timings[0] = 0; // Android patterns start with a delay
        for (int i = 0; i < parts.length; i++) {
            try {
                long v = Long.parseLong(parts[i].trim());
                timings[i + 1] = Math.max(0, Math.min(200, v));
            } catch (NumberFormatException e) {
                timings[i + 1] = 0;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, -1)); // -1 = do not repeat
        } else {
            vibrator.vibrate(timings, -1);
        }
    }
}