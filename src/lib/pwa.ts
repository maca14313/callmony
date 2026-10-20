"use client";

import { useEffect, useRef, useState } from "react";

/* PWA plumbing: service-worker registration, version check, install prompt. */

export const APP_VERSION = "1.2.0";

export function registerSW(onUpdateReady?: () => void) {
  if (typeof window === "undefined" || !("serviceWorker" in navigator)) return;
  const start = async () => {
    try {
      const reg = await navigator.serviceWorker.register("/sw.js");
      reg.addEventListener("updatefound", () => {
        const nw = reg.installing;
        nw?.addEventListener("statechange", () => {
          if (nw.state === "installed" && navigator.serviceWorker.controller) {
            /* Activate the waiting worker now so the user's reload (toast) lands
               on the new SW immediately instead of the next navigation. */
            reg.waiting?.postMessage({ type: "SKIP_WAITING" });
            onUpdateReady?.();
          }
        });
      });
    } catch {
      /* SW not available — app still works */
    }
  };
  /* Hydration effects often run AFTER the window "load" event has already fired
     (especially on reloads) — in that case a load listener would never trigger
     and the SW would never register. Register immediately when the document is
     already complete, otherwise wait for load. */
  if (document.readyState === "complete") void start();
  else window.addEventListener("load", () => void start());
}

/** Fetches /api/version when online; shows update toast when it changes. */
export async function checkVersion(onUpdateReady: (v: string) => void) {
  if (typeof window === "undefined" || !navigator.onLine) return;
  try {
    const res = await fetch("/api/version", { cache: "no-store" });
    if (!res.ok) return;
    const json = (await res.json()) as { version: string };
    const known = localStorage.getItem("callmony-sw-version");
    if (known && known !== json.version) {
      onUpdateReady(json.version);
    }
    localStorage.setItem("callmony-sw-version", json.version);
  } catch {
    /* offline */
  }
}

/** Light periodic version poll while online — runs checkVersion on an interval
 *  and whenever the browser comes back online. Returns a stop() function.
 *  Additive helper (not wired into App.tsx; the mount + online listener there
 *  already covers the basics — this is for longer-lived sessions). */
export function startVersionPolling(
  onUpdateReady: (v: string) => void,
  intervalMs: number = 6 * 60 * 60 * 1000,
): () => void {
  if (typeof window === "undefined") return () => {};
  const run = () => void checkVersion(onUpdateReady);
  const timer = window.setInterval(run, intervalMs);
  window.addEventListener("online", run);
  return () => {
    window.clearInterval(timer);
    window.removeEventListener("online", run);
  };
}

export function useInstallPrompt(): {
  canInstall: boolean;
  promptInstall: () => Promise<boolean>;
  isIOS: boolean;
} {
  const [deferred, setDeferred] = useState<
    (Event & { prompt: () => Promise<void>; userChoice?: Promise<{ outcome: string }> }) | null
  >(null);
  const [isIOS] = useState(() => {
    if (typeof navigator === "undefined") return false;
    return (
      /ipad|iphone|ipod/i.test(navigator.userAgent) ||
      (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1)
    );
  });

  useEffect(() => {
    const onBIP = (e: Event) => {
      e.preventDefault();
      setDeferred(e as Event & { prompt: () => Promise<void> });
    };
    window.addEventListener("beforeinstallprompt", onBIP);
    return () => window.removeEventListener("beforeinstallprompt", onBIP);
  }, []);

  return {
    canInstall: !!deferred,
    isIOS,
    promptInstall: async () => {
      if (!deferred) return false;
      await deferred.prompt();
      setDeferred(null);
      return true;
    },
  };
}

/** Request persistent storage so the browser won't evict local data. */
export async function requestPersistence(): Promise<boolean> {
  try {
    if (navigator.storage?.persist) return await navigator.storage.persist();
  } catch {
    /* noop */
  }
  return false;
}

/** Alarm scheduler hook driver — returns true periodically to re-check reminders. */
export function useTick(ms: number): number {
  const [tick, setTick] = useState(Date.now());
  const ref = useRef(0);
  useEffect(() => {
    ref.current = window.setInterval(() => setTick(Date.now()), ms);
    return () => clearInterval(ref.current);
  }, [ms]);
  return tick;
}
