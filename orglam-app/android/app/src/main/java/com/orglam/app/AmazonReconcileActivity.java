package com.orglam.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class AmazonReconcileActivity extends Activity {

    private WebView web;
    private String shipmentId;
    private String skus;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        String url = getIntent().getStringExtra("url");
        if (url == null) url = "https://sellercentral.amazon.eg/home";
        shipmentId = getIntent().getStringExtra("shipmentId");
        skus = getIntent().getStringExtra("skus");

        web = new WebView(this);
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // Seller Central withholds the reconcile form from anything identifying as a WebView.
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0.0.0 Mobile Safari/537.36");

        // Persisting cookies is the point: sign in once, and later visits are one tap.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                if (u == null) return;

                if (u.contains("/inbound-shipment/summary/")
                        && shipmentId != null && skus != null && !skus.isEmpty()) {
                    view.evaluateJavascript(prefillScript(), null);
                    return;
                }

                // Opened purely to establish the session (no shipment passed): once Seller
                // Central renders a signed-in page the cookie exists, so record it and close.
                if (shipmentId == null && !u.contains("signin") && !u.contains("/ap/")) {
                    CookieManager.getInstance().flush();
                    getSharedPreferences("orglam", MODE_PRIVATE)
                            .edit().putBoolean("amazon_signed_in", true).apply();
                    Toast.makeText(AmazonReconcileActivity.this,
                            "Amazon connected", Toast.LENGTH_LONG).show();
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() { finish(); }
                    }, 1200);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return false;
            }
        });

        web.loadUrl(url);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /**
     * Marks the short SKUs as queried on the visible page, leaving the document upload and the
     * Submit press to you. Used by "Open on Amazon"; silent filing lives in the bridge.
     */
    private String prefillScript() {
        String sid = shipmentId == null ? "" : shipmentId.replaceAll("[^A-Za-z0-9]", "");
        String pairs = skus == null ? "" : skus.replace("'", "");
        return
        "(async function(){" +
        "  if (window.__orglamWmsr) return; window.__orglamWmsr = true;" +
        "  var sid = '" + sid + "';" +
        "  var want = {};" +
        "  '" + pairs + "'.split(',').filter(Boolean).forEach(function(p){" +
        "    var b = p.split('='); want[b[0]] = (b[1] === 'ACK' ? 'ACK' : 'UNKNOWN');" +
        "  });" +
        "  function toast(m){ try { var d=document.createElement('div'); d.textContent=m;" +
        "    d.style.cssText='position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:99999;background:#232f3e;color:#fff;padding:12px 18px;border-radius:10px;font:14px system-ui;max-width:88%;text-align:center';" +
        "    document.body.appendChild(d); setTimeout(function(){d.remove();}, 6000); } catch(e){} }" +
        "  try {" +
        "    var cr = await fetch('/fba/wmsr/csrf', {credentials:'include'});" +
        "    var ct = await cr.text();" +
        "    var m = ct.match(/anti-csrftoken-a2z[\"'\\s:=]+([A-Za-z0-9+/=]{20,})/);" +
        "    if (!m) { toast('Could not read the security token - select the SKUs manually.'); return; }" +
        "    var tok = m[1];" +
        "    var dr = await fetch('/fba/wmsr/discrepancies/' + sid + '?filter=DISCREPANCY&index=0&pageSize=50&isOrderAscending=true&orderBy=MSKU', {credentials:'include'});" +
        "    var dj = await dr.json();" +
        "    var rows = (dj && (dj.discrepancies || dj.items || dj.data)) || [];" +
        "    var done = 0;" +
        "    for (var i = 0; i < rows.length; i++) {" +
        "      var r = rows[i]; var sku = r.merchantSku || r.msku || r.sellerSku;" +
        "      if (!sku || !want[sku]) continue;" +
        "      var res = await fetch('/fba/wmsr/inquiries/' + sid + '/items', {method:'PUT', credentials:'include'," +
        "        headers:{'Content-Type':'application/json','anti-csrftoken-a2z':tok}," +
        "        body: JSON.stringify({merchantSku:sku, fnsku:r.fnsku, asin:r.asin, reasonCode:want[sku]})});" +
        "      if (res.ok) done++;" +
        "    }" +
        "    if (done) { toast(done + ' SKU(s) marked. Attach your document and press Submit.'); location.reload(); }" +
        "    else toast('Nothing was pre-selected - choose the SKUs manually.');" +
        "  } catch (e) { toast('Pre-fill failed: ' + e.message); }" +
        "})();";
    }
}