/* System call log (Android) — react-native-call-log wrapper. */

import { Platform, PermissionsAndroid } from "react-native";
import CallLog from "react-native-call-log";
import type { PermState } from "./deviceContacts";

export interface DeviceCall {
  id: string;
  phoneNumber: string;
  name?: string;
  direction: "incoming" | "outgoing" | "missed";
  timestamp: number; // epoch ms
  duration: number; // seconds
}

const CALL_TYPES: Record<string, DeviceCall["direction"]> = {
  // numeric codes returned by the Android CallLog.Calls provider
  "1": "incoming",
  "2": "outgoing",
  "3": "missed",
  "4": "incoming", // voicemail
  "5": "missed", // rejected
  "6": "missed", // blocked
  "7": "incoming", // answered externally
  INCOMING: "incoming",
  OUTGOING: "outgoing",
  MISSED: "missed",
  REJECTED: "missed",
  BLOCKED: "missed",
};

export const isCallLogAvailable = (): boolean => Platform.OS === "android";

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
    return res === PermissionsAndroid.RESULTS.GRANTED ? "authorized" : "denied";
  } catch {
    return "denied";
  }
};

/** Load the most recent `limit` system calls (newest first). */
export const fetchSystemCalls = async (limit = 400): Promise<DeviceCall[]> => {
  if (Platform.OS !== "android") return [];
  const raw = (await CallLog.load(limit)) as Array<Record<string, unknown>>;
  return (raw || [])
    .map((c, i) => {
      const typeRaw = String(c.callType ?? c.type ?? "").toUpperCase().replace(/\s/g, "");
      const ts = Number(c.callDate ?? c.timestamp ?? 0);
      const parsed = !isNaN(ts) && ts > 0 ? ts : Date.parse(String(c.callDayTime ?? "")) || 0;
      return {
        id: `sys-${ts}-${i}`,
        phoneNumber: String(c.phoneNumber ?? c.number ?? ""),
        name: (c.cachedName as string) || undefined,
        direction: CALL_TYPES[typeRaw] ?? "incoming",
        timestamp: parsed || Date.now() - i * 60000,
        duration: Number(c.duration ?? 0) || 0,
      } satisfies DeviceCall;
    })
    .filter((c) => c.timestamp > 0)
    .sort((a, b) => b.timestamp - a.timestamp);
};
