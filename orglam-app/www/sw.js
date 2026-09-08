const CACHE_NAME = 'orglam-shell-v15';
const SHELL_URL = './Orglam Dashboard.html';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll([SHELL_URL, './manifest.json', './icon.jpg', './icon-rounded.png', './icon-badge.png', './icon-badge-bold.png']))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('push', (event) => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch (e) {}
  const title = data.title || 'Orglam';
  const options = {
    body: data.body || '',
    icon: './icon-rounded.png',
    badge: './icon-badge-notif.png',
    tag: data.tag || undefined,
    renotify: !!data.tag,
    requireInteraction: true,
    // A reminder is an alarm: a long insistent buzz and an action right on the notification, so it
    // can be answered from the lock screen without opening the app.
    vibrate: data.alarm ? [500, 200, 500, 200, 500, 200, 500] : [200, 100, 200],
    data: { url: data.url || './Orglam Dashboard.html', alarm: !!data.alarm, tag: data.tag || '' },
    actions: data.alarm ? [{ action: 'todo-done', title: 'Mark done' }, { action: 'todo-snooze', title: 'Snooze 10 min' }] : undefined,
  };
  // Classify so an open tab can play a distinct in-app sound per type (Web Push has no way to
  // set a custom OS notification sound - that's controlled by Android's system channel, not us).
  const tag = data.tag || '';
  const category = tag.startsWith('order-amazon-') || tag.startsWith('order-noon-') ? 'amazon_noon_order' : tag.startsWith('return-compensation-') ? 'compensation' : title.startsWith('Order ') ? 'shopify_order' : 'message';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
      list.forEach((c) => c.postMessage({ type: 'pushSound', category, title, body: data.body || '' }));
      // Hand the actual message to the app so an open chat can show it immediately - the push
      // carries the text, whereas re-reading it from storage lags behind by design.
      if (data.convKey) list.forEach((c) => c.postMessage({ type: 'liveMessage', convKey: data.convKey, msgType: data.msgType || 'text', msgText: data.msgText || data.body || '', msgTs: Number(data.msgTs) || Date.now(), title }));
      // Any open app window/tab (even backgrounded, not just focused) already gets its own custom
      // sound via the postMessage above - showing the native OS notification WITH its own sound
      // too is what caused the double-ring. Keep the notification visible (tray/lock screen) but
      // mute its sound whenever the app is running anywhere; only let the OS play its own sound
      // when the app isn't running at all and nothing else will make a sound for this alert.
      // An alarm must make noise even while the app is open in the background - the in-app chime
      // only plays in a FOREGROUND window, so muting this one on any open client is what would let
      // a reminder pass in silence.
      options.silent = data.alarm ? false : list.length > 0;
      return self.registration.showNotification(title, options);
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || './Orglam Dashboard.html';
  // Answering from the notification itself: the tap has to tell the app, which then tells the
  // server, or the re-push loop keeps ringing a reminder that has already been dealt with.
  if (event.notification.data?.alarm && event.action) {
    const taskId = String(event.notification.data.tag || '').replace(/^todo-/, '');
    event.waitUntil(
      self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
        list.forEach((c) => c.postMessage({ type: 'todoAlarmAction', action: event.action, taskId }));
        if (!list.length && self.clients.openWindow) {
          return self.clients.openWindow(`${targetUrl}${targetUrl.includes('?') ? '&' : '?'}todoAction=${encodeURIComponent(event.action)}&todoTask=${encodeURIComponent(taskId)}`);
        }
      })
    );
    return;
  }
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          // Post the deep-link to the already-running app instead of navigating/reloading it -
          // a full navigate() reloads the whole SPA, which feels like leaving the app instead
          // of a smooth in-place transition (like the in-app notifications panel gives).
          client.postMessage({ type: 'deepLink', url: targetUrl });
          return client.focus();
        }
      }
      if (self.clients.openWindow) return self.clients.openWindow(targetUrl);
    })
  );
});

// Network-first for the app SHELL only (so you always get the latest dashboard when online),
// falling back to cache when offline. Staff open this app at the shop with NO connection, where a
// bare network-first fetch sits waiting on a socket that will never answer - so the network gets
// only a short head start before the cached shell is served instead.
//
// Live data must NEVER go through this. Caching API responses and then racing them against a
// 2.5s timer meant any slow call (a ticket conversation, a Bosta scan) lost the race and the app
// was handed an OLDER cached copy - which is why recent messages went missing until an action
// forced a fresh load. API calls now always go straight to the network.
const isShellRequest = (request) => {
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return false; // the Worker API lives on another origin
  return /\.(html|json|png|jpg|jpeg|svg|ico|webmanifest|js|css)$/i.test(url.pathname) || url.pathname === '/' || url.pathname.endsWith('/');
};

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  // Never cache API traffic - it is live data, and a stale answer reads as a bug.
  if (/workers\.dev|\/api\//.test(event.request.url)) return;
  if (!isShellRequest(event.request)) return; // let the browser handle live data itself
  // The app HTML gets a much longer head start than the other assets. At 2.5s a merely SLOW
  // connection lost the race and the app silently ran yesterday's code - fixes appeared to do
  // nothing at all. A genuinely offline phone fails its fetch in well under a second, so the
  // offline-at-the-shop case is unaffected by the longer wait.
  const isAppHtml = /\.html$/i.test(new URL(event.request.url).pathname) || event.request.mode === 'navigate';
  const cacheAfter = isAppHtml ? 12000 : 2500;
  event.respondWith(
    caches.match(event.request).then((cached) => {
      const network = fetch(event.request)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
          return res;
        });
      if (!cached) return network.catch(() => cached);
      return Promise.race([
        network.catch(() => cached),
        new Promise((resolve) => setTimeout(() => resolve(cached), cacheAfter)),
      ]);
    })
  );
});
