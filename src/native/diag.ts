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
  setNavBarColor(color: string, lightIcons: boolean): Promise<boolean>;
  isIgnoringBatteryOptimizations(): Promise<boolean>;
  requestIgnoreBatteryOptimizations(): Promise<boolean>;
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

/* v1.6.1 — keep the system navigation bar in sync with the resolved app
   theme (the static styles.xml color is light-only, so dark mode used to
   leave a mismatched bar). fire-and-forget: never blocks, never throws. */
export const setNavBarColor = (color: string, lightIcons: boolean): void => {
  if (Platform.OS !== "android" || !Diag) return;
  try {
    void Diag.setNavBarColor(color, lightIcons).catch(() => undefined);
  } catch {
    /* cosmetics — never crash over it */
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

/* ---------------- v1.6.3 — reminder reliability (battery saver) ----------------
   OEM battery managers (Tecno/Infinix/Xiaomi/Samsung "app sleep") are the
   #1 reason scheduled alarms never fire. isBatteryExempt() reports the real
   OS state; requestBatteryExemption() opens the one-tap system dialog. */

export const isBatteryExempt = async (): Promise<boolean | null> => {
  if (Platform.OS !== "android" || !Diag) return null;
  try {
    return await Diag.isIgnoringBatteryOptimizations();
  } catch {
    return null;
  }
};

export const requestBatteryExemption = async (): Promise<boolean> => {
  if (Platform.OS !== "android" || !Diag) return false;
  try {
    return await Diag.requestIgnoreBatteryOptimizations();
  } catch {
    return false;
  }
};
