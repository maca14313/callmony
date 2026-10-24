/* callmony service worker — 100% offline-first app shell.
   Plain JS, no build step. Bump VERSION to ship an update (old caches drop on activate).

   Strategy:
   - Navigations are CACHE-FIRST (stale-while-revalidate): a cached shell answers
     instantly (the app opens offline with ZERO network wait) while a background
     fetch keeps the cached shell fresh, so a newer deploy is already in the cache
     for the next launch. The page-side flow (/api/version check → toast → reload)
     needs no postMessage handshake.
   - Immutable assets (/_next/static/, /icons/, /manifest.json): cache-first with
     background revalidate.
   - Other same-origin GETs: cache-first, network fallback.
   - /api/ routes are network-only — never cached. */

const VERSION = "1.2.0";
const CACHE = "callmony-v-" + VERSION;

/* Precached on install. Icons are generated after first deploy, so every entry is
   fetched independently and a missing file never breaks installation. */
const PRECACHE_URLS = [
  "/",
  "/manifest.json",
  "/icons/icon-192.png",
  "/icons/icon-512.png",
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      try {
        const cache = await caches.open(CACHE);
        await Promise.allSettled(PRECACHE_URLS.map((url) => cache.add(url).catch(() => {})));
      } catch (err) {
        /* precache is best-effort */
      }
      await self.skipWaiting();
    })()
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
      );
      await self.clients.claim();
    })()
  );
});

/* Lets the page activate a waiting worker immediately. */
self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

/* Cache a response without ever letting a rejected cache.put bubble up. */
const putIn = (cache, req, res) => {
  try {
    return cache.put(req, res).catch(() => undefined);
  } catch (err) {
    return undefined;
  }
};

/* Inline fallback for the very first visit when nothing is cached yet and the
   network is down (friendly, branded, served as 200 text/html). */
const offlinePage = () =>
  new Response(
    [
      "<!doctype html>",
      '<html lang="en"><head><meta charset="utf-8">',
      '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">',
      "<title>callmony — offline</title>",
      "<style>",
      ":root{color-scheme:light dark}",
      "body{margin:0;min-height:100dvh;display:flex;align-items:center;justify-content:center;",
      "background:#f7faf9;color:#172b2a;font:16px/1.6 system-ui,-apple-system,sans-serif;",
      "text-align:center;padding:24px}",
      ".mark{width:56px;height:56px;border-radius:18px;background:#16a085;color:#fff;",
      "display:flex;align-items:center;justify-content:center;font-weight:700;font-size:24px;",
      "margin:0 auto 16px}",
      "h1{font-size:20px;margin:0 0 8px}",
      "p{margin:0;opacity:.72;font-size:14px}",
      "@media (prefers-color-scheme:dark){body{background:#0e1514;color:#eef4f2}}",
      "</style></head><body><div>",
      '<div class="mark">c</div>',
      "<h1>callmony — offline</h1>",
      "<p>Reopen once online to finish installing.</p>",
      "</div></body></html>",
    ].join(""),
    {
      status: 200,
      statusText: "OK",
      headers: { "Content-Type": "text/html; charset=utf-8" },
    }
  );

self.addEventListener("fetch", (event) => {
  const req = event.request;

  if (req.method !== "GET") return;

  let url;
  try {
    url = new URL(req.url);
  } catch (err) {
    return;
  }

  /* same-origin only */
  if (url.origin !== self.location.origin) return;

  /* API routes: network only — never cached */
  if (url.pathname.startsWith("/api/")) return;

  /* Hashed build assets, icons and the manifest: cache-first with a background
     refresh (stale-while-revalidate). */
  const isImmutable =
    url.pathname.startsWith("/_next/static/") ||
    url.pathname.startsWith("/icons/") ||
    url.pathname === "/manifest.json";

  if (isImmutable) {
    const cachePromise = caches.open(CACHE);
    const update = cachePromise
      .then((cache) =>
        fetch(req)
          .then((res) => {
            if (res && res.ok && res.type === "basic") putIn(cache, req, res.clone());
            return res;
          })
          .catch(() => null)
      )
      .catch(() => null);
    event.waitUntil(update);
    event.respondWith(
      (async () => {
        const cache = await cachePromise;
        const cached = await cache.match(req);
        if (cached) return cached;
        const fresh = await update;
        if (fresh) return fresh;
        return new Response("Offline", { status: 503, statusText: "Offline" });
      })()
    );
    return;
  }

  /* Page navigations: CACHE-FIRST stale-while-revalidate — the offline-first core.
     1. A cached shell (this request, else "/") responds IMMEDIATELY: the app opens
        instantly, fully offline, with zero network wait.
     2. A background fetch(req) ALWAYS runs (event.waitUntil) and, when the server
        has a newer deploy, cache.put("/", res) self-updates the shell — so the
        existing update flow (/api/version → toast → reload) lands on the new
        version without any postMessage handshake.
     3. Nothing cached at all (very first visit): try the network; if that fails,
        serve the inline offline page. */
  if (req.mode === "navigate") {
    const cachePromise = caches.open(CACHE);
    const update = cachePromise
      .then((cache) =>
        fetch(req)
          .then((res) => {
            if (res && res.ok && res.type === "basic") putIn(cache, "/", res.clone());
            return res;
          })
          .catch(() => null)
      )
      .catch(() => null);
    event.waitUntil(update);
    event.respondWith(
      (async () => {
        const cache = await cachePromise;
        const cached = (await cache.match(req)) || (await cache.match("/"));
        if (cached) return cached;
        const fresh = await update;
        if (fresh) return fresh;
        return offlinePage();
      })()
    );
    return;
  }

  /* Everything else same-origin: cache first, then network; successful basic
     responses are cached for next time. */
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const cached = await cache.match(req);
      if (cached) return cached;
      try {
        const res = await fetch(req);
        if (res && res.ok && res.type === "basic") putIn(cache, req, res.clone());
        return res;
      } catch (err) {
        return new Response("Offline", { status: 503, statusText: "Offline" });
      }
    })()
  );
});
