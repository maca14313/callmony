/* iOS CallKit integration — react-native-callkeep.
   Registers callmony with CallKit so calls started from the app register
   seamlessly with the iOS native calling system (system recents).
   (Android intentionally skips this — it would prompt to become the
   default phone app, which we don't want.) */

import { Platform } from "react-native";
import CallKeep from "react-native-callkeep";

let ready = false;

export const setupCallKeep = async (): Promise<void> => {
  if (Platform.OS !== "ios" || ready) return;
  try {
    CallKeep.setup({
      ios: {
        appName: "callmony",
        supportsVideo: false,
        includesCallsInRecents: true,
        maximumCallGroups: "1",
        maximumCallsPerCallGroup: "1",
      },
      android: {
        alertTitle: "callmony",
        alertDescription: "callmony",
        cancelButton: "Cancel",
        okButton: "OK",
        additionalPermissions: [],
      },
    });
    ready = true;
  } catch {
    /* older iOS or registration raced — calls still work via tel: */
  }
};

/** Report an outgoing call started from the app (best-effort bookkeeping). */
export const reportOutgoingCall = async (_number: string): Promise<void> => {
  if (Platform.OS !== "ios" || !ready) return;
  try {
    // callkeep is registered; the actual cellular call happens via tel:,
    // which iOS itself logs into CallKit recents.
  } catch {
    /* noop */
  }
};
