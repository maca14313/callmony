/* IndexedDB layer — all data lives on the device.
   v3 schema: keyed stores use in-line ids; `meta` + `blobs` use out-of-line keys. */

const DB_NAME = "dial-ledger";
const DB_VERSION = 3;

/** stores keyed by in-line "id" property */
export const KEYED_STORES = [
  "contacts",
  "entries",
  "groups",
  "lists",
  "notes",
  "reminders",
  "banks",
  "recents",
  "messages",
  "attachments",
] as const;

export type StoreName = (typeof KEYED_STORES)[number] | "meta" | "blobs";

let dbPromise: Promise<IDBDatabase | null> | null = null;

export function openDB(): Promise<IDBDatabase | null> {
  if (typeof indexedDB === "undefined") return Promise.resolve(null);
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve) => {
    try {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = (event) => {
        const db = req.result;
        const oldVersion = (event as IDBVersionChangeEvent).oldVersion ?? 0;
        KEYED_STORES.forEach((s) => {
          if (!db.objectStoreNames.contains(s)) db.createObjectStore(s, { keyPath: "id" });
        });
        // meta: key/value store with OUT-OF-LINE keys (v1 wrongly used keyPath — recreate)
        if (!db.objectStoreNames.contains("meta")) {
          db.createObjectStore("meta");
        } else if (oldVersion < 2) {
          db.deleteObjectStore("meta");
          db.createObjectStore("meta");
        }
        // blobs: id → Blob (out-of-line keys)
        if (!db.objectStoreNames.contains("blobs")) db.createObjectStore("blobs");
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  return dbPromise;
}

async function tx<T>(
  store: StoreName,
  mode: IDBTransactionMode,
  fn: (s: IDBObjectStore) => IDBRequest
): Promise<T | undefined> {
  const db = await openDB();
  if (!db) return undefined;
  return new Promise((resolve) => {
    try {
      const t = db.transaction(store, mode);
      const req = fn(t.objectStore(store));
      req.onsuccess = () => resolve(req.result as T);
      req.onerror = () => resolve(undefined);
    } catch {
      resolve(undefined);
    }
  });
}

export const idbGetAll = <T>(store: (typeof KEYED_STORES)[number]): Promise<T[]> =>
  tx<T[]>(store, "readonly", (s) => s.getAll()).then((r) => r || []);

export const idbPut = <T extends { id: string }>(store: (typeof KEYED_STORES)[number], value: T): Promise<void> =>
  tx(store, "readwrite", (s) => s.put(value)).then(() => undefined);

export const idbPutKey = (store: StoreName, key: string, value: unknown): Promise<void> =>
  tx(store, "readwrite", (s) => s.put(value, key)).then(() => undefined);

export const idbGetKey = <T>(store: StoreName, key: string): Promise<T | undefined> =>
  tx<T>(store, "readonly", (s) => s.get(key));

export const idbDel = (store: StoreName, key: string): Promise<void> =>
  tx(store, "readwrite", (s) => s.delete(key)).then(() => undefined);

export const idbClear = (store: StoreName): Promise<void> =>
  tx(store, "readwrite", (s) => s.clear()).then(() => undefined);

/** meta helpers (settings, flags) — key/value JSON with out-of-line keys */
export const metaGet = <T>(key: string): Promise<T | undefined> =>
  tx<{ key: string; value: T } | undefined>("meta", "readonly", (s) => s.get(key)).then((r) => r?.value);

export const metaSet = <T>(key: string, value: T): Promise<void> =>
  idbPutKey("meta", key, { key, value });

export const metaDel = (key: string): Promise<void> => idbDel("meta", key);

/** blobs: id → Blob */
export const blobPut = (id: string, blob: Blob): Promise<void> =>
  tx("blobs", "readwrite", (s) => s.put(blob, id)).then(() => undefined);

export const blobGet = (id: string): Promise<Blob | undefined> =>
  tx<Blob>("blobs", "readonly", (s) => s.get(id));

export const blobDel = (id: string): Promise<void> =>
  tx("blobs", "readwrite", (s) => s.delete(id)).then(() => undefined);
