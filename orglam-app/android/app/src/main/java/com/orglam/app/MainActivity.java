package com.orglam.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[]{
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      }, 1001);
    }

    getBridge().getWebView().getSettings().setGeolocationEnabled(true);

    // Native vibrator: Chrome refuses to vibrate before a tap, and a scroll never counts as one,
    // so scroll haptics stayed dead until something was pressed. Native has no such rule.
    getBridge().getWebView().addJavascriptInterface(new HapticsBridge(this), "AndroidHaptics");

    // Opens Bosta's real login in a window the app is allowed to read, so the wallet session
    // renews without copying a token from a computer.
    getBridge().getWebView().addJavascriptInterface(new BostaLoginBridge(this), "AndroidBostaLogin");

    // Same for Jumia. Jumia signs in through Google and issues no refresh token, so this window
    // is the only way to get a session without developer tools on a computer.
    getBridge().getWebView().addJavascriptInterface(new JumiaLoginBridge(this), "AndroidJumiaLogin");

    // Files Amazon research requests inside your live Seller Central session, so a missing-unit
    // claim can be sent from the app without opening Seller Central by hand.
    getBridge().getWebView().addJavascriptInterface(new AmazonReconcileBridge(this), "AndroidAmazonReconcile");

    // Installs an APK sent as a WhatsApp document, straight from the message thread - a browser can
    // only download the file, it cannot hand it to Android's installer.
    getBridge().getWebView().addJavascriptInterface(new ApkInstallBridge(this), "AndroidApkInstall");

    getBridge().getWebView().setWebChromeClient(new com.getcapacitor.BridgeWebChromeClient(getBridge()) {
      @Override
      public void onPermissionRequest(final PermissionRequest request) {
        runOnUiThread(() -> request.grant(request.getResources()));
      }

      // Camera and mic come through onPermissionRequest above; location uses this separate hook.
      // Without it the WebView answers "no" on its own, which is the "location access denied" you saw.
      @Override
      public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        callback.invoke(origin, true, false);
      }
    });
  }
}