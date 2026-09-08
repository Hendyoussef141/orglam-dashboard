package com.orglam.app;   // <-- must match your MainActivity's package

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class BostaLoginActivity extends Activity {

    // Where the captured token is sent. Must match BASE_URL in the dashboard.
    private static final String WORKER = "https://dawn-king-6cc3.orglam-service.workers.dev";
    private static final String LOGIN_URL = "https://business.bosta.co/signin";

    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        web = new WebView(this);
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // Bosta keeps its session in localStorage
        s.setDatabaseEnabled(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void done(final boolean ok, final String message) {
                runOnUiThread(() -> {
                    Toast.makeText(BostaLoginActivity.this,
                            ok ? "Bosta connected" : ("Bosta: " + message),
                            Toast.LENGTH_LONG).show();
                    if (ok) finish();
                });
            }
        }, "AndroidBostaCapture");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(captureScript(), null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return false; // keep every navigation inside this window
            }
        });

        web.loadUrl(LOGIN_URL);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /**
     * Injected into every Bosta page. Two ways to find the token, because Bosta may change
     * where it keeps it:
     *   1. watch outgoing requests for the Authorization header they already send
     *   2. scan localStorage / sessionStorage for a JWT
     * The first one to succeed posts the token to the Worker and reports back.
     */
    private String captureScript() {
        return
        "(function(){" +
        "  if (window.__orglamCapture) return; window.__orglamCapture = true;" +
        "  var sent = false;" +
        "  function send(tok){" +
        "    if (sent || !tok) return; sent = true;" +
        // Bosta's wallet API only accepts "Bearer <jwt>". The Authorization header already carries
        // the prefix; a token read out of storage does not, so add it when it's missing.
        "    if (tok.indexOf('Bearer') !== 0) tok = 'Bearer ' + tok;" +
        "    fetch('" + WORKER + "/bosta-wallet-token', {method:'POST'," +
        "      headers:{'Content-Type':'application/json'}," +
        "      body: JSON.stringify({token: tok})})" +
        "      .then(function(r){return r.json();})" +
        "      .then(function(d){" +
        "        if (d && d.error) { sent = false; AndroidBostaCapture.done(false, d.error); }" +
        "        else AndroidBostaCapture.done(true, '');" +
        "      })" +
        "      .catch(function(e){ sent = false; AndroidBostaCapture.done(false, 'network error'); });" +
        "  }" +
        // 1. the header Bosta's own requests carry
        "  var origSet = XMLHttpRequest.prototype.setRequestHeader;" +
        "  XMLHttpRequest.prototype.setRequestHeader = function(k, v){" +
        "    try { if (String(k).toLowerCase() === 'authorization' && v) send(v); } catch(e){}" +
        "    return origSet.apply(this, arguments);" +
        "  };" +
        "  var origFetch = window.fetch;" +
        "  window.fetch = function(input, init){" +
        "    try {" +
        "      var h = (init && init.headers) || (input && input.headers);" +
        "      if (h) {" +
        "        if (typeof h.get === 'function') { var a = h.get('Authorization') || h.get('authorization'); if (a) send(a); }" +
        "        else { Object.keys(h).forEach(function(k){ if (k.toLowerCase() === 'authorization' && h[k]) send(h[k]); }); }" +
        "      }" +
        "    } catch(e){}" +
        "    return origFetch.apply(this, arguments);" +
        "  };" +
        // 2. anything JWT-shaped already in storage
        "  function scan(){" +
        "    if (sent) return;" +
        "    [localStorage, sessionStorage].forEach(function(store){" +
        "      try {" +
        "        for (var i = 0; i < store.length; i++) {" +
        "          var raw = store.getItem(store.key(i));" +
        "          if (!raw) continue;" +
        "          var m = String(raw).match(/eyJ[A-Za-z0-9_\\\\-]+\\\\.[A-Za-z0-9_\\\\-]+\\\\.[A-Za-z0-9_\\\\-]+/);" +
        "          if (m) { send(m[0]); return; }" +
        "        }" +
        "      } catch(e){}" +
        "    });" +
        "  }" +
        "  scan();" +
        "  var tries = 0;" +
        "  var t = setInterval(function(){ tries++; scan(); if (sent || tries > 40) clearInterval(t); }, 1500);" +
        "})();";
    }
}