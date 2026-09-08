package com.orglam.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class JumiaLoginActivity extends Activity {

    private static final String WORKER = "https://dawn-king-6cc3.orglam-service.workers.dev";
    private static final String LOGIN_URL = "https://vendorcenter.jumia.com/sign-in";

    private WebView web;
    private volatile boolean sent = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        web = new WebView(this);
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                try {
                    String host = req.getUrl() == null ? "" : String.valueOf(req.getUrl().getHost());
                    boolean isJumiaApi = host.endsWith(".jumia.com") && host.startsWith("api-");
                    if (!sent && isJumiaApi) {
                        Map<String, String> h = req.getRequestHeaders();
                        if (h != null) {
                            for (Map.Entry<String, String> e : h.entrySet()) {
                                if (e.getKey() != null && e.getKey().equalsIgnoreCase("Authorization")) {
                                    postToken(e.getValue());
                                }
                            }
                        }
                    }
                } catch (Exception ignored) { }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return false;
            }
        });

        web.loadUrl(LOGIN_URL);
    }

    private void postToken(String rawHeader) {
        if (rawHeader == null) return;
        final String token = rawHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (!token.startsWith("eyJ") || token.length() < 60) return;
        if (sent) return;
        sent = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                String message = "";
                try {
                    URL u = new URL(WORKER + "/jumia-token");
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setDoOutput(true);
                    OutputStream os = c.getOutputStream();
                    os.write(("{\"raw\":\"" + token + "\"}").getBytes("UTF-8"));
                    os.close();
                    ok = c.getResponseCode() == 200;
                    if (!ok) message = "server said " + c.getResponseCode();
                    c.disconnect();
                } catch (Exception ex) {
                    message = ex.getMessage() == null ? "network error" : ex.getMessage();
                }
                final boolean fOk = ok;
                final String fMsg = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(JumiaLoginActivity.this,
                                fOk ? "Jumia connected" : ("Jumia: " + fMsg),
                                Toast.LENGTH_LONG).show();
                        if (fOk) finish();
                        else sent = false;
                    }
                });
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}