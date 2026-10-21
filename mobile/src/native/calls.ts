/* ------------------------------------------------------------------ *
 * Immediate direct calls — NO simulation, ever.
 *
 * Android: react-native-immediate-phone-call fires Intent.ACTION_CALL
 *   (the phone's native calling system starts instantly — the number is
 *   NOT dropped into the keypad). Uses the CALL_PHONE permission,
 *   requested at runtime; if the user denies it we fall back to a
 *   ACTION_DIAL-style tel: handoff (number pre-filled, one tap).
 * iOS: the same library opens tel: — iOS shows its native calling UI
 *   and the call lands in the system (CallKit) recents.
 * ------------------------------------------------------------------ */

import { Platform, Linking, PermissionsAndroid } from "react-native";
import RNImmediatePhoneCall from "react-native-immediate-phone-call";
import { useApp } from "../lib/store";
import { contactByPhone } from "../lib/store";
import { normalizePhone } from "../lib/utils";
import { contactOwnerId } from "../lib/types";

export type CallResult = "placed" | "dialer" | "denied" | "blocked" | "failed";

/** Is this number blocked in settings? (blocked numbers never ring) */
const isBlocked = (number: string): boolean => {
  const norm = normalizePhone(number, useApp.getState().settings.countryCode);
  return useApp.getState().settings.blockedNumbers.includes(norm);
};

/** Runtime CALL_PHONE permission (dangerous, must be requested). */
export const ensureCallPermission = async (): Promise<boolean> => {
  if (Platform.OS !== "android") return true;
  try {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CALL_PHONE,
      {
        title: "callmony",
        message: "Allow callmony to start calls directly without opening the keypad?",
        buttonPositive: "Allow",
        buttonNegative: "Not now",
      }
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  } catch {
    return false;
  }
};

/**
 * Place a real phone call immediately.
 * Android: ACTION_CALL (instant, native dialer takes over).
 * iOS: tel: open (native calling UI; logged by CallKit).
 */
export const placeCall = async (number: string): Promise<CallResult> => {
  if (!number) return "failed";
  if (isBlocked(number)) return "blocked";

  const hasPermission =
    Platform.OS === "android" ? await ensureCallPermission() : true;

  const ok = await new Promise<boolean>((resolve) => {
    try {
      RNImmediatePhoneCall.immediatePhoneCall(number);
      // the native side is fire-and-forget; give the OS a beat
      setTimeout(() => resolve(true), 120);
    } catch {
      resolve(false);
    }
  });

  if (!ok) {
    // library unavailable → tel: handoff (number pre-filled)
    try {
      await Linking.openURL(`tel:${number}`);
      logAppCall(number, hasPermission ? "placed" : "dialer");
      return "dialer";
    } catch {
      return "failed";
    }
  }

  if (!hasPermission && Platform.OS === "android") {
    // permission denied mid-flow — the library already fell back internally
    logAppCall(number, "dialer");
    return "dialer";
  }

  logAppCall(number, "placed");
  return "placed";
};

/** Log the call in the app's own recents (used on iOS, where the system
 *  log is not readable; on Android the real call shows up in the system
 *  log, so we only record it for the in-app view of calls we started). */
const logAppCall = (number: string, _via: "placed" | "dialer") => {
  const contact = contactByPhone(number);
  if (Platform.OS === "ios") {
    useApp.getState().addCall({
      phone: number,
      ownerId: contact ? contactOwnerId(contact.recordID) : undefined,
      direction: "outgoing",
      duration: 0,
      via: "tel",
    });
  }
};
