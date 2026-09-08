package com.orglam.app;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;

public class BostaLoginBridge {
    private final Activity activity;

    public BostaLoginBridge(Activity activity) { this.activity = activity; }

    @JavascriptInterface
    public void open() {
        activity.runOnUiThread(() ->
                activity.startActivity(new Intent(activity, BostaLoginActivity.class)));
    }
}