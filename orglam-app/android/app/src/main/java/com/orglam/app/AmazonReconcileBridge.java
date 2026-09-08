package com.orglam.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class AmazonReconcileBridge {
    private final Context context;

    public AmazonReconcileBridge(Context context) {
        this.context = context;
    }

    @JavascriptInterface
    public void open(String url) {
        Intent i = new Intent(context, AmazonReconcileActivity.class);
        i.putExtra("url", url);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    @JavascriptInterface
    public void openWithItems(String url, String shipmentId, String skus) {
        Intent i = new Intent(context, AmazonReconcileActivity.class);
        i.putExtra("url", url);
        i.putExtra("shipmentId", shipmentId);
        i.putExtra("skus", skus);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    /**
     * Whether a Seller Central sign-in has happened on this device. The dashboard cannot read
     * Amazon's cookie across origins, so this flag is the only signal it has. It says "signed
     * in at some point", not that the cookie is still valid - Amazon expires it on its own
     * schedule, and a claim attempt is what discovers that.
     */
    @JavascriptInterface
    public boolean hasSession() {
        return context
                .getSharedPreferences("orglam", Context.MODE_PRIVATE)
                .getBoolean("amazon_signed_in", false);
    }

    @JavascriptInterface
    public void markSession() {
        context.getSharedPreferences("orglam", Context.MODE_PRIVATE)
                .edit().putBoolean("amazon_signed_in", true).apply();
    }

    /**
     * Files the claim with no visible page. The script runs on a Seller Central page so it
     * inherits the session cookie, then makes the same four requests the browser makes:
     * read the CSRF token, mark each SKU, upload the statement, submit.
     */
    @JavascriptInterface
    public void fileClaim(final String shipmentId, final String skus, String unusedProof) {
        if (!(context instanceof Activity)) return;
        final Activity act = (Activity) context;
        act.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final WebView hidden = new WebView(act);
                WebSettings st = hidden.getSettings();
                st.setJavaScriptEnabled(true);
                st.setDomStorageEnabled(true);
                // Desktop user agent: Seller Central serves a different, reduced layout to
                // anything identifying as mobile.
                st.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
                st.setUseWideViewPort(true);
                st.setLoadWithOverviewMode(true);
                CookieManager.getInstance().setAcceptCookie(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(hidden, true);

                // Full size so the page initialises normally, but translated far off-screen
                // and non-interactive, so it is never seen and never intercepts a touch.
                hidden.setAlpha(0f);
                hidden.setEnabled(false);
                hidden.setTranslationX(-100000f);

                final ViewGroup root = (ViewGroup) act.getWindow().getDecorView();
                DisplayMetrics dm = act.getResources().getDisplayMetrics();
                root.addView(hidden, new ViewGroup.LayoutParams(dm.widthPixels, dm.heightPixels));

                hidden.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void claimResult(final boolean ok, final String message) {
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (hidden.getParent() != null) root.removeView(hidden);
                                hidden.destroy();
                                Toast.makeText(act,
                                        ok ? ("Amazon case " + message) : ("Amazon: " + message),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }, "AndroidClaim");

                hidden.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String u) {
                        if (u == null) return;
                        if (u.contains("/inbound-shipment/summary/") || u.contains("/fba/wmsr")) {
                            view.evaluateJavascript(AmazonClaimScript.build(shipmentId, skus), null);
                        } else if (u.contains("signin") || u.contains("/ap/")) {
                            // Not signed in: filing is impossible, so report it rather than
                            // sitting on an invisible login page. The stale flag is cleared
                            // so the Settings row stops claiming a session.
                            context.getSharedPreferences("orglam", Context.MODE_PRIVATE)
                                    .edit().putBoolean("amazon_signed_in", false).apply();
                            view.evaluateJavascript(
                                    "AndroidClaim.claimResult(false, 'sign in from Settings "
                                    + "> Amazon Session first');", null);
                        }
                    }
                });

                // The shipment's own page: it renders reliably and puts the script on the
                // Seller Central origin, which is all the requests need.
                hidden.loadUrl("https://sellercentral.amazon.eg/fba/inbound-shipment/summary/"
                        + shipmentId + "/contents");

                // If Amazon never answers, tear the view down instead of leaving it attached.
                hidden.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (hidden.getParent() != null) {
                            root.removeView(hidden);
                            hidden.destroy();
                            Toast.makeText(act, "Amazon did not respond - try again",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }, 150000);
            }
        });
    }
}