/* v1.6.9 — PIN persistence OUTSIDE the backup set (user report: "I removed
   the pin, uninstalled the app, installed the new version — the pin is
   STILL there").

   Root cause: the pin hash lived in the persisted settings (AsyncStorage),
   and Android Auto Backup restores AsyncStorage on reinstall. A pin
   removed after the last backup upload came straight back with the
   restored data.

   Fix: the pin hash now lives in its own tiny file inside the OS
   `no_backup` directory — the directory Android explicitly EXCLUDES from
   Auto Backup and cloud restore (same place Wallets/Keystore keep their
   device-local data). Removing the pin deletes the file; uninstalling
   wipes it; a reinstall restores the app DATA (ledger, reminders — the
   no-data-loss guarantee) but never the pin.

   The in-memory settings.pinHash keeps driving the lock screen exactly as
   before — only its persistence moved. */

import { Platform } from "react-native";
import RNFS from "react-native-fs";
import type { Settings } from "./types";

type PinHash = Settings["pinHash"];

/* DocumentDirectoryPath = <data>/files — the OS-provided no_backup dir is
   its sibling <data>/no_backup (never uploaded by Auto Backup). */
const PIN_DIR = `${RNFS.DocumentDirectoryPath}/../no_backup`;
const PIN_FILE = `${PIN_DIR}/pin.json`;

let dirEnsured = false;

const ensureDir = async (): Promise<void> => {
  if (dirEnsured) return;
  try {
    const exists = await RNFS.exists(PIN_DIR);
    if (!exists) await RNFS.mkdir(PIN_DIR);
    dirEnsured = true;
  } catch {
    /* falls back to a write error below — pin simply won't persist */
  }
};

/** Read the stored pin hash (null = no pin / fresh install / removed). */
export const readPinFile = async (): Promise<PinHash | null> => {
  if (Platform.OS === "web") return null;
  try {
    const txt = await RNFS.readFile(PIN_FILE, "utf8");
    const parsed = JSON.parse(txt) as PinHash;
    if (parsed && typeof parsed.hash === "string" && typeof parsed.salt === "string") return parsed;
    return null;
  } catch {
    return null;
  }
};

/** Persist the pin hash (set / change). Fire-and-forget safe. */
export const writePinFile = async (pin: PinHash): Promise<void> => {
  if (!pin) return removePinFile();
  try {
    await ensureDir();
    await RNFS.writeFile(PIN_FILE, JSON.stringify(pin), "utf8");
  } catch {
    /* storage issue: the in-app lock still works this session */
  }
};

/** Remove the pin file — REMOVAL IS FOREVER, even across reinstall. */
export const removePinFile = async (): Promise<void> => {
  try {
    await RNFS.unlink(PIN_FILE);
  } catch {
    /* file already gone */
  }
};
