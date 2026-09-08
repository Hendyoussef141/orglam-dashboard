package com.orglam.app;

public class AmazonClaimScript {
    public static String build(String shipmentId, String skus) {
        String sid = shipmentId == null ? "" : shipmentId.replaceAll("[^A-Za-z0-9]", "");
        String pairs = skus == null ? "" : skus.replace("'", "");
        return
        "(async function(){" +
        "  if (window.__orglamFile) return; window.__orglamFile = true;" +
        "  var sid = '" + sid + "';" +
        "  var want = {};" +
        "  '" + pairs + "'.split(',').filter(Boolean).forEach(function(p){" +
        "    var b = p.split('='); want[b[0]] = (b[1] === 'ACK' ? 'ACK' : 'UNKNOWN');" +
        "  });" +
        "  function done(ok, msg){ try { AndroidClaim.claimResult(ok, String(msg)); } catch(e){} }" +
        "  try {" +
        // The token sits on the /fba/wmsr/csrf page as <meta content="..." name="csrf-token">.
        // Parsing the document is what makes this reliable - matching on attribute order in
        // raw HTML is what failed before, since content comes BEFORE name here.
        "    var cr = await fetch('/fba/wmsr/csrf', {credentials:'include'});" +
        "    var ct = await cr.text();" +
        "    var doc = new DOMParser().parseFromString(ct, 'text/html');" +
        "    var meta = doc.querySelector('meta[name=\"csrf-token\"]');" +
        "    var tok = meta && meta.getAttribute('content');" +
        "    if (!tok) {" +
        "      var mm = ct.match(/name=\"csrf-token\"[^>]*content=\"([^\"]+)\"/)" +
        "             || ct.match(/content=\"([^\"]+)\"[^>]*name=\"csrf-token\"/);" +
        "      tok = mm && mm[1];" +
        "    }" +
        "    if (!tok) return done(false, 'security token not found');" +
        "    var H = {'Content-Type':'application/json','anti-csrftoken-a2z':tok};" +
        "    var dr = await fetch('/fba/wmsr/discrepancies/' + sid + '?filter=DISCREPANCY&index=0&pageSize=50&isOrderAscending=true&orderBy=MSKU', {credentials:'include'});" +
        "    if (!dr.ok) return done(false, 'could not read the discrepancy list (' + dr.status + ')');" +
        "    var dj = await dr.json();" +
        "    var rows = (dj && (dj.discrepancies || dj.items || dj.data || dj.discrepancyList)) || [];" +
        "    if (!rows.length) return done(false, 'Amazon reports no discrepancies for this shipment');" +
        "    var marked = 0, matched = [], amzSkus = [], lastStatus = '';" +
        "    for (var i = 0; i < rows.length; i++) {" +
        "      var r = rows[i];" +
        // Amazon's key name for the seller SKU is not documented; take the first that exists
        // so a rename on their side cannot silently produce "nothing claimable".
        "      var sku = r.merchantSku || r.msku || r.sellerSku || r.sellerSKU || r.merchantSKU || r.mskuId;" +
        "      if (sku) amzSkus.push(sku);" +
        "      if (!sku || !want[sku]) continue;" +
        "      var pr = await fetch('/fba/wmsr/inquiries/' + sid + '/items', {" +
        "        method:'PUT', credentials:'include', headers:H," +
        "        body: JSON.stringify({merchantSku:sku, fnsku:r.fnsku, asin:r.asin, reasonCode:want[sku]})});" +
        "      lastStatus = pr.status;" +
        "      if (pr.ok) { marked++; matched.push(r); }" +
        "    }" +
        "    if (!marked) {" +
        // Report exactly what Amazon returned versus what was asked for, rather than a blanket
        // "nothing claimable" that gives nothing to act on.
        "      if (!amzSkus.length) return done(false, 'unknown row shape: ' + Object.keys(rows[0] || {}).join(',').slice(0, 90));" +
        "      var overlap = amzSkus.filter(function(s){ return want[s]; });" +
        "      if (!overlap.length) return done(false, 'Amazon lists ' + amzSkus.slice(0,2).join(', ') + ' - app wanted ' + Object.keys(want).slice(0,2).join(', '));" +
        "      return done(false, 'Amazon refused the mark (' + lastStatus + ')');" +
        "    }" +
        // A document is required only when something is being researched; the statement is
        // drawn from Amazon's own figures so it can never contradict their page.
        "    var claimed = matched.filter(function(r){ return want[r.merchantSku || r.msku || r.sellerSku || r.sellerSKU || r.merchantSKU || r.mskuId] === 'UNKNOWN'; });" +
        "    if (claimed.length) {" +
        "      var W = 1240, pad = 60, Ht = 420 + claimed.length * 46;" +
        "      var cv = document.createElement('canvas'); cv.width = W; cv.height = Ht;" +
        "      var g = cv.getContext('2d');" +
        "      g.fillStyle = '#fff'; g.fillRect(0,0,W,Ht);" +
        "      g.fillStyle = '#2b2b26'; g.font = '700 34px Helvetica,Arial,sans-serif';" +
        "      g.fillText('Inbound Shipment Discrepancy Statement', pad, 86);" +
        "      g.font = '400 20px Helvetica,Arial,sans-serif'; g.fillStyle = '#5d5d55';" +
        "      g.fillText('Shipment ' + sid, pad, 126);" +
        "      g.fillText('Prepared ' + new Date().toLocaleDateString('en-GB',{day:'numeric',month:'long',year:'numeric'}), pad, 156);" +
        "      g.strokeStyle = '#d8d6ce'; g.beginPath(); g.moveTo(pad,186); g.lineTo(W-pad,186); g.stroke();" +
        "      function skuOf(r){ return r.merchantSku || r.msku || r.sellerSku || r.sellerSKU || r.merchantSKU || r.mskuId; }" +
        "      function exp(r){ return Number(r.expectedQuantity != null ? r.expectedQuantity : (r.shippedQuantity || 0)); }" +
        "      function loc(r){ return Number(r.locatedQuantity != null ? r.locatedQuantity : (r.receivedQuantity || 0)); }" +
        "      var tExp = 0, tLoc = 0;" +
        "      claimed.forEach(function(r){ tExp += exp(r); tLoc += loc(r); });" +
        "      g.fillStyle = '#2b2b26'; g.font = '400 22px Helvetica,Arial,sans-serif';" +
        "      g.fillText('Units expected: ' + tExp, pad, 228);" +
        "      g.fillText('Units located by Amazon: ' + tLoc, pad, 262);" +
        "      g.font = '700 22px Helvetica,Arial,sans-serif';" +
        "      g.fillText('Units unaccounted for: ' + (tExp - tLoc), pad, 296);" +
        "      g.font = '700 18px Helvetica,Arial,sans-serif'; g.fillStyle = '#5d5d55';" +
        "      g.fillText('MSKU', pad, 352); g.fillText('EXPECTED', pad+620, 352);" +
        "      g.fillText('LOCATED', pad+790, 352); g.fillText('MISSING', pad+950, 352);" +
        "      g.font = '400 19px Helvetica,Arial,sans-serif'; g.fillStyle = '#2b2b26'; g.strokeStyle = '#e8e6de';" +
        "      claimed.forEach(function(r, i){" +
        "        var y = 392 + i*46;" +
        "        g.fillText(String(skuOf(r)).slice(0,42), pad, y);" +
        "        g.fillText(String(exp(r)), pad+620, y);" +
        "        g.fillText(String(loc(r)), pad+790, y);" +
        "        g.fillText(String(exp(r) - loc(r)), pad+950, y);" +
        "        g.beginPath(); g.moveTo(pad, y+14); g.lineTo(W-pad, y+14); g.stroke();" +
        "      });" +
        "      g.font = '400 17px Helvetica,Arial,sans-serif'; g.fillStyle = '#6b6b62';" +
        "      g.fillText('All units listed above were packed and dispatched in this shipment.', pad, Ht-46);" +
        "      var blob = await new Promise(function(res){ cv.toBlob(res, 'image/png'); });" +
        "      var fd = new FormData();" +
        "      fd.append('shipmentID', sid);" +
        "      fd.append('proofType', 'INVOICE');" +
        // The field name is file1, not file - taken from the captured request.
        "      fd.append('file1', blob, 'discrepancy-statement-' + sid + '.png');" +
        "      var ur = await fetch('/fba/wmsr-document-upload', {method:'POST', credentials:'include'," +
        "        headers:{'anti-csrftoken-a2z':tok}, body: fd});" +
        "      var uj = await ur.json().catch(function(){ return {}; });" +
        "      if (!ur.ok || uj.errorMessage) return done(false, 'document rejected: ' + (uj.errorMessage || ur.status));" +
        "    }" +
        // Submit carries no body - only the token.
        "    var sr = await fetch('/fba/wmsr/inquiries/' + sid, {method:'POST', credentials:'include'," +
        "      headers:{'anti-csrftoken-a2z':tok}});" +
        "    var sj = await sr.json().catch(function(){ return {}; });" +
        "    if (sj.caseId) return done(true, sj.caseId);" +
        "    if (sr.ok) return done(true, 'submitted');" +
        "    done(false, 'submit failed (' + sr.status + ')');" +
        "  } catch (e) { done(false, e.message); }" +
        "})();";
    }
}