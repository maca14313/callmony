/* v1.5.2 — diagnostics bridge (Android).
   bootMark(label)      — fire-and-forget breadcrumb written to disk; if the
                          app dies between milestones we know exactly where.
   readCrashReport()    — the native crash captured by CrashReporter.kt the
                          last time the process died ("" when none).
   clearCrashReport()   — wipe the stored report + marks. */

import { NativeModules, Platform } from "react-native";

interface DiagNative {
  mark(label: string): void;
  readCrashReport(): Promise<string>;
  clearCrashReport(): Promise<boolean>;
  readBootMarks(): Promise<string>;
}

const Diag = NativeModules.CallMonyDiag as DiagNative | undefined;

export const bootMark = (label: string): void => {
  if (Platform.OS !== "android" || !Diag) return;
  try {
    Diag.mark(label);
  } catch {
    /* diagnostics must never affect the boot path */
  }
};

export const readCrashReport = async (): Promise<string> => {
  if (Platform.OS !== "android" || !Diag) return "";
  try {
    return (await Diag.readCrashReport()) || "";
  } catch {
    return "";
  }
};

export const clearCrashReport = async (): Promise<void> => {
  if (Platform.OS !== "android" || !Diag) return;
  try {
    await Diag.clearCrashReport();
  } catch {
    /* noop */
  }
};
