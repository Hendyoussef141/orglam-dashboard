package com.orglam.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Installs an APK that arrived as a WhatsApp document, or the published update. The file is
 * downloaded to the app's own cache (no storage permission needed), then handed to Android's
 * package installer through a FileProvider URI - a raw file:// URI is rejected on Android 7+.
 *
 * Register in MainActivity.onCreate:
 *   bridge.getWebView().addJavascriptInterface(new ApkInstallBridge(this), "AndroidApkInstall");
 */
public class ApkInstallBridge {

    private final Context ctx;
    /**
     * Download progress, 0-100, read by the web side's updating screen. -1 means "not downloading",
     * which is what tells the web side to fall back to a paced estimate rather than showing a bar
     * frozen at zero.
     */
    private volatile double progressPct = -1;

    public ApkInstallBridge(Context ctx) {
        this.ctx = ctx;
    }

    @JavascriptInterface
    public void install(final String url, final String fileName) {
        progressPct = 0;
        new Thread(() -> {
            File out = null;
            try {
                String safe = fileName == null || fileName.isEmpty() ? "update.apk"
                    : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                if (!safe.toLowerCase().endsWith(".apk")) safe = safe + ".apk";

                File dir = new File(ctx.getCacheDir(), "apk");
                if (!dir.exists()) dir.mkdirs();
                out = new File(dir, safe);
                // A part-written file from an earlier failed attempt would install as corrupt.
                if (out.exists()) out.delete();

                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(120000);
                c.setInstanceFollowRedirects(true);
                c.connect();

                if (c.getResponseCode() / 100 != 2) {
                    toast("Download failed (" + c.getResponseCode() + ")");
                    progressPct = -1;
                    c.disconnect();
                    return;
                }

                // Content-Length is what makes the bar real rather than indeterminate; a chunked
                // response reports -1, and the web side then eases the bar along by itself.
                final long total = c.getContentLength();
                long done = 0;

                try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        done += n;
                        if (total > 0) progressPct = Math.min(99, (done * 100.0) / total);
                    }
                    fos.flush();
                }
                c.disconnect();

                if (out.length() < 1024) {
                    toast("That file doesn't look like an app");
                    out.delete();
                    progressPct = -1;
                    return;
                }

                progressPct = 100;
                launchInstaller(out);
            } catch (Exception e) {
                if (out != null && out.exists()) out.delete();
                progressPct = -1;
                toast("Could not download the app");
            }
        }).start();
    }

    /** 0-100 while downloading, 100 when the installer is being opened, -1 when idle or failed. */
    @JavascriptInterface
    public double progress() {
        return progressPct;
    }

    private void launchInstaller(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            // The installer runs in another process, so it needs explicit read access to our file.
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            progressPct = -1;
            toast("Android refused to open the installer");
        }
    }

    /** This build's versionCode and name, so the web side can tell if a newer one is published. */
    @JavascriptInterface
    public int versionCode() {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? (int) pi.getLongVersionCode()
                : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    @JavascriptInterface
    public String versionName() {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Whether this build can install apps at all - lets the web side hide the button if not. */
    @JavascriptInterface
    public boolean canInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                return ctx.getPackageManager().canRequestPackageInstalls();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /** Opens the system screen where the user grants "install unknown apps" for this app. */
    @JavascriptInterface
    public void openInstallPermissionSettings() {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) { }
    }

    private void toast(final String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }
}
