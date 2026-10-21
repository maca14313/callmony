/* ------------------------------------------------------------------ *
 * Immediate direct calls — NO simulation, ever.
 *
 * Android: our own ImmediateCall native module fires Intent.ACTION_CALL.
 *   With the CALL_PHONE runtime permission granted, the phone's DEFAULT
 *   dialer/phone app places the call instantly — the number is never
 *   dropped into the keypad. The permission is requested once at app
 *   start (dialer-style) and re-requested here if it was denied.
 *   If the user permanently denies it we fall back to ACTION_DIAL (the
 *   system keypad with the number pre-filled, one tap to call).
 * iOS: tel: handoff — iOS shows its native calling UI and the call lands
 *   in the system (CallKit) recents.
 * ------------------------------------------------------------------ */

import { Linking, NativeModules, PermissionsAndroid, Platform } from "react-native";
import { useApp, contactByPhone } from "../lib/store";
import { normalizePhone } from "../lib/utils";
import { contactOwnerId } from "../lib/types";

const ImmediateCall = NativeModules.ImmediateCall as
  | {
      immediateCall(number: string): Promise<string>;
      dial(number: string): Promise<string>;
    }
  | undefined;

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
 * Android: ACTION_CALL via the default phone app (instant — no keypad).
 * iOS: tel: open (native calling UI; logged by CallKit).
 */
export const placeCall = async (number: string): Promise<CallResult> => {
  if (!number) return "failed";
  if (isBlocked(number)) return "blocked";

  /* Android: native module — immediate ACTION_CALL through the default dialer */
  if (Platform.OS === "android" && ImmediateCall) {
    try {
      let result = await ImmediateCall.immediateCall(number);
      if (result === "denied") {
        const granted = await ensureCallPermission();
        if (!granted) {
          // no CALL_PHONE permission → system dialer with the number pre-filled
          await ImmediateCall.dial(number).catch(() => undefined);
          logAppCall(number, "dialer");
          return "dialer";
        }
        result = await ImmediateCall.immediateCall(number);
      }
      if (result === "placed") {
        logAppCall(number, "placed");
        return "placed";
      }
      // call didn't start (no phone app / SecurityException) → pre-filled dialer
      await ImmediateCall.dial(number).catch(() => undefined);
      logAppCall(number, "dialer");
      return "dialer";
    } catch {
      try {
        await Linking.openURL(`tel:${number}`);
        logAppCall(number, "dialer");
        return "dialer";
      } catch {
        return "failed";
      }
    }
  }

  /* iOS (and safety net): native tel: handoff */
  try {
    await Linking.openURL(`tel:${number}`);
    logAppCall(number, "dialer");
    return "dialer";
  } catch {
    return "failed";
  }
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
