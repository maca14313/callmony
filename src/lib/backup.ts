/* Auto email sync — the offline-honest backup engine.
   On a schedule (daily / weekly) callmony builds:
     1. callmony-backup-<stamp>.json  — ALL app data (ledger, lists, notes,
        reminders, banks, settings, app-side contact extensions)
     2. callmony-contacts-<stamp>.vcf — every device contact as vCard
   and opens the user's email app with BOTH files attached and the
   recipient pre-filled — the user just presses Send. Nothing is ever
   uploaded anywhere: no accounts, no SMTP credentials, full round-trip
   via Settings → Restore from backup. */

import RNFS from "react-native-fs";
import { useApp } from "./store";
import { contactToVcard } from "./exporters";
import { composeEmail } from "../native/email";
import { dateStr } from "./utils";

const BACKUP_DIR = `${RNFS.DocumentDirectoryPath}/backups`;

const stamp = (): string => {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${dateStr(d).replace(/-/g, "")}-${p(d.getHours())}${p(d.getMinutes())}`;
};

/** Everything the app knows, in the same shape Settings → Restore reads. */
export const buildBackupPayload = (): Record<string, unknown> => {
  const s = useApp.getState();
  return {
    app: "callmony",
    version: 1,
    exportedAt: Date.now(),
    data: {
      contactX: s.contactX,
      entries: s.entries,
      groups: s.groups,
      lists: s.lists,
      notes: s.notes,
      reminders: s.reminders,
      banks: s.banks,
      recents: s.recents,
      messages: s.messages,
      settings: {
        language: s.settings.language,
        currency: s.settings.currency,
        countryCode: s.settings.countryCode,
      },
    },
  };
};

/** Write the backup files to the app's private backups/ folder.
    @returns the absolute file paths (empty array on failure). */
export const writeBackupFiles = async (): Promise<string[]> => {
  try {
    await RNFS.mkdir(BACKUP_DIR);
    const files: string[] = [];

    /* 1 — full data backup */
    const jsonPath = `${BACKUP_DIR}/callmony-backup-${stamp()}.json`;
    await RNFS.writeFile(jsonPath, JSON.stringify(buildBackupPayload()), "utf8");
    files.push(jsonPath);

    /* 2 — contacts as vCard (import straight into any email account) */
    const contacts = useApp.getState().deviceContacts;
    if (contacts.length) {
      const vcfPath = `${BACKUP_DIR}/callmony-contacts-${dateStr(new Date()).replace(/-/g, "")}.vcf`;
      await RNFS.writeFile(vcfPath, contacts.map(contactToVcard).join("\n"), "utf8");
      files.push(vcfPath);
    }

    /* keep the folder small — newest 8 files only */
    const mt = (f: { mtime?: Date }) => (f.mtime ? f.mtime.getTime() : 0);
    const all = (await RNFS.readDir(BACKUP_DIR)).sort((a, b) => mt(b) - mt(a));
    for (const old of all.slice(8)) {
      await RNFS.unlink(old.path).catch(() => undefined);
    }
    return files;
  } catch {
    return [];
  }
};

/**
 * Build everything and open the email composer with it attached.
 * @returns "sent" when the composer opened, "fail" when it did not.
 */
export const syncNow = async (): Promise<"sent" | "fail"> => {
  const settings = useApp.getState().settings;
  const files = await writeBackupFiles();
  if (!files.length) return "fail";
  const ok = await composeEmail({
    to: settings.autoBackupEmail || "",
    subject: "callmony backup",
    body:
      "Your callmony backup is attached:\n" +
      "• callmony-backup-….json — ledger, lists, notes, reminders, banks, settings\n" +
      "• callmony-contacts-….vcf — all contacts (import into any email account)\n\n" +
      "Keep this email safe — restore anytime from Settings → Backup & restore.",
    files,
  });
  if (ok) {
    useApp.getState().updateSettings({ lastBackupAt: Date.now(), lastAutoSyncAt: Date.now() });
    return "sent";
  }
  return "fail";
};

/**
 * Auto trigger — called when the app becomes active. Opens the composer
 * only when the configured interval has elapsed (and an email is set).
 */
export const maybeAutoSync = async (): Promise<void> => {
  const { autoBackupEmail, autoBackupEvery, lastAutoSyncAt } = useApp.getState().settings;
  if (!autoBackupEmail || !autoBackupEvery || autoBackupEvery <= 0) return;
  if (lastAutoSyncAt && Date.now() - lastAutoSyncAt < autoBackupEvery * 86400000) return;
  await syncNow();
};

/* ---------------- v1.6.3 — automatic local SAFETY SNAPSHOTS ----------------
   "No data should be lost — the safest app in the world." On every app open
   (throttled to 6h) the FULL data set is written to
   Documents/backups/callmony-backup-auto-<stamp>.json next to the email
   backups. The folder is inside Android Auto Backup's scope (files domain),
   so the snapshots survive even an uninstall→reinstall on a new phone via
   Google's device restore — THREE independent copies of everything:
     1. AsyncStorage primary + mirror (crash/torn-write safe)
     2. local snapshot files (app data restore)
     3. Google Auto Backup (device loss/reset) + optional email backups   */

const SNAPSHOT_THROTTLE = 6 * 3600 * 1000;

export const autoSnapshot = async (force = false): Promise<boolean> => {
  try {
    const { lastSnapshotAt } = useApp.getState().settings;
    if (!force && lastSnapshotAt && Date.now() - lastSnapshotAt < SNAPSHOT_THROTTLE) return false;
    const files = await writeBackupFiles();
    if (!files.length) return false;
    useApp.getState().updateSettings({ lastSnapshotAt: Date.now() });
    return true;
  } catch {
    return false;
  }
};
