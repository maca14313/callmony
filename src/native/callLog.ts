/* System call log (Android) — our own SysCallLog native module.
   Reads the same CallLog.Calls provider the built-in dialer uses. */

import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import RNFS from "react-native-fs";
import type { PermState } from "./deviceContacts";

export interface DeviceCall {
  id: string;
  phoneNumber: string;
  name?: string;
  direction: "incoming" | "outgoing" | "missed";
  timestamp: number; // epoch ms
  duration: number; // seconds
}

interface RawSysCall {
  id: string;
  name?: string;
  phoneNumber: string;
  callType: number;
  timestamp: number;
  duration: number;
}

const SysCallLog = NativeModules.SysCallLog as
  | {
      load(limit: number): Promise<RawSysCall[]>;
      loadSince(since: number, limit: number): Promise<RawSysCall[]>;
    }
  | undefined;

/** android.provider.CallLog.Calls type codes → direction */
const CALL_TYPES: Record<number, DeviceCall["direction"]> = {
  1: "incoming",
  2: "outgoing",
  3: "missed",
  4: "incoming", // voicemail
  5: "missed", // rejected
  6: "missed", // blocked
  7: "incoming", // answered externally
};

export const isCallLogAvailable = (): boolean => Platform.OS === "android" && !!SysCallLog;

export const requestCallLogPermission = async (): Promise<PermState> => {
  if (Platform.OS !== "android") return "denied";
  try {
    const res = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
      {
        title: "callmony",
        message: "Allow callmony to read your call log so Recents shows your real calls?",
        buttonPositive: "Allow",
        buttonNegative: "Not now",
      }
    );
    if (res === PermissionsAndroid.RESULTS.GRANTED) return "authorized";
    if (res === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) return "denied";
    return "denied";
  } catch {
    return "denied";
  }
};

/** shared bridge-rows → DeviceCall mapping (newest first). */
const mapRawCalls = (raw: RawSysCall[] | null | undefined): DeviceCall[] =>
  (raw || [])
    .map((c, i) => ({
      id: c.id || `sys-${c.timestamp}-${i}`,
      phoneNumber: String(c.phoneNumber ?? ""),
      name: c.name || undefined,
      direction: CALL_TYPES[c.callType] ?? "incoming",
      timestamp: Number(c.timestamp) || 0,
      duration: Number(c.duration) || 0,
    }))
    .filter((c) => c.timestamp > 0)
    .sort((a, b) => b.timestamp - a.timestamp);

/** Load the most recent `limit` system calls (newest first). */
export const fetchSystemCalls = async (limit = 400): Promise<DeviceCall[]> => {
  if (Platform.OS !== "android" || !SysCallLog) return [];
  return mapRawCalls(await SysCallLog.load(limit));
};

/** v1.6.11 — INCREMENTAL sync: only rows logged AFTER `since` (epoch ms).
    The stock-dialer behavior users expect: opening the app re-reads just the
    handful of NEW calls (usually zero) instead of re-serializing the whole
    400-row log over the bridge on every open. Falls back to a full read when
    the native side predates loadSince (older APK). */
export const fetchCallsSince = async (since: number, limit = 200): Promise<DeviceCall[]> => {
  if (Platform.OS !== "android" || !SysCallLog) return [];
  if (typeof SysCallLog.loadSince !== "function") return fetchSystemCalls(400);
  return mapRawCalls(await SysCallLog.loadSince(Math.max(0, Math.floor(since)), limit));
};

/** Rejected with a "denied" message when READ_CALL_LOG is missing. */
export const isPermissionDeniedError = (err: unknown): boolean =>
  String((err as { message?: string })?.message ?? err).toLowerCase().includes("denied");

/* ---------------- disk snapshot (instant Recents on cold start) ---------------- */

const CALLS_CACHE = `${RNFS.DocumentDirectoryPath}/calllog-cache.json`;

export interface CallsCache {
  savedAt: number;
  list: DeviceCall[];
}

/** Read the cached call-log snapshot — resolves an EMPTY cache when there is
    none. v1.6.11 also returns savedAt so the sync layer can tell a FRESH
    snapshot (incremental refresh is enough) from a STALE one (a full
    re-read is due — that is also how deletions get reconciled). */
export const loadCachedCalls = async (): Promise<CallsCache> => {
  try {
    const txt = await RNFS.readFile(CALLS_CACHE, "utf8");
    const parsed = JSON.parse(txt) as { savedAt?: number; list?: DeviceCall[] };
    return {
      savedAt: Number(parsed?.savedAt) || 0,
      list: Array.isArray(parsed?.list) ? parsed.list : [],
    };
  } catch {
    return { savedAt: 0, list: [] };
  }
};

/** Persist the snapshot (fire-and-forget — never blocks the UI). */
export const saveCachedCalls = (list: DeviceCall[]): void => {
  RNFS.writeFile(CALLS_CACHE, JSON.stringify({ savedAt: Date.now(), list }), "utf8").catch(
    () => undefined
  );
};
