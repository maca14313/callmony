/* System call log (Android) — our own SysCallLog native module.
   Reads the same CallLog.Calls provider the built-in dialer uses. */

import { NativeModules, PermissionsAndroid, Platform } from "react-native";
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
  | { load(limit: number): Promise<RawSysCall[]> }
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

/** Load the most recent `limit` system calls (newest first). */
export const fetchSystemCalls = async (limit = 400): Promise<DeviceCall[]> => {
  if (Platform.OS !== "android" || !SysCallLog) return [];
  const raw = await SysCallLog.load(limit);
  return (raw || [])
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
};

/** Rejected with a "denied" message when READ_CALL_LOG is missing. */
export const isPermissionDeniedError = (err: unknown): boolean =>
  String((err as { message?: string })?.message ?? err).toLowerCase().includes("denied");
