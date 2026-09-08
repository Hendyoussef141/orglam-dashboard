package com.orglam.app;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

public class JumiaLoginBridge {
    private final Context context;

    public JumiaLoginBridge(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void open() {
        Intent i = new Intent(context, JumiaLoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}