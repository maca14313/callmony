/* dial. service worker — offline-first app-shell cache.
   Plain JS, no build step. Bump VERSION to ship an update (old caches drop on activate). */

const VERSION = "1.0.0";
const CACHE = "dial-v-" + VERSION;

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

  /* Page navigations: network-first; cache the fresh shell, fall back to any
     cached page (the shell at "/") when offline. */
  if (req.mode === "navigate") {
    event.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        try {
          const res = await fetch(req);
          if (res && res.ok && res.type === "basic") putIn(cache, "/", res.clone());
          return res;
        } catch (err) {
          const cachedPage = (await cache.match(req)) || (await cache.match("/"));
          if (cachedPage) return cachedPage;
          return new Response("You are offline and the dial. app shell is not cached yet.", {
            status: 503,
            headers: { "Content-Type": "text/plain; charset=utf-8" },
          });
        }
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
