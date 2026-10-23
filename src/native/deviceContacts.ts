/* Device contacts — react-native-contacts wrapper.
   The address book is loaded ONCE per stale window and kept as a disk
   snapshot (JSON file): the app opens with contacts INSTANTLY from the
   cache — the OS is only queried again when the snapshot is older than
   CONTACTS_TTL (5 min) or after an in-app contact edit. A 5000-contact
   book never re-serializes over the bridge on every open. */

import { Platform, PermissionsAndroid } from "react-native";
import Contacts from "react-native-contacts";
import RNFS from "react-native-fs";
import type { DeviceContact } from "../lib/types";

export type PermState = "authorized" | "denied" | "undetermined";

/** contacts older than this are re-fetched from the OS (ms). */
export const CONTACTS_TTL = 5 * 60 * 1000;

const CACHE_FILE = `${RNFS.DocumentDirectoryPath}/contacts-cache.json`;

export interface ContactsCache {
  savedAt: number;
  count: number;
  list: DeviceContact[];
}

const normalizeLabel = (label: string): string => {
  const l = (label || "").toLowerCase().replace(/[_\s]/g, "");
  if (l.includes("mobile") || l.includes("cell")) return "mobile";
  if (l.includes("work") || l.includes("company")) return "work";
  if (l.includes("home")) return "home";
  if (l.includes("main")) return "main";
  if (l.includes("office")) return "office";
  return "other";
};

const normalizeBirthday = (b?: Contacts.Contact["birthday"]): string | undefined => {
  if (!b) return undefined;
  // v7 types: { day, month, year } (or an epoch string on some builds)
  if (typeof b === "object" && b != null && "year" in b && "month" in b && "day" in b) {
    const { year, month, day } = b as { year: number; month: number; day: number };
    if (!year || !month || !day) return undefined;
    const p = (x: number) => String(x).padStart(2, "0");
    return `${year}-${p(month)}-${p(day)}`;
  }
  const s = String(b);
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
  const n = Number(s);
  if (!isNaN(n) && n > 0) {
    const d = new Date(n * 1000);
    const p = (x: number) => String(x).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }
  return undefined;
};

const toDeviceContact = (c: Contacts.Contact): DeviceContact => {
  const given = c.givenName || "";
  const family = c.familyName || "";
  const composed = `${given} ${family}`.trim();
  return {
    recordID: c.recordID,
    displayName: composed || c.displayName || c.company || "",
    givenName: given,
    familyName: family,
    company: c.company || undefined,
    jobTitle: c.jobTitle || undefined,
    note: c.note || undefined,
    birthday: normalizeBirthday(c.birthday),
    phones: (c.phoneNumbers || []).map((p) => ({
      id: p.number,
      label: normalizeLabel(p.label),
      number: p.number,
    })),
    emails: (c.emailAddresses || []).map((e) => ({
      id: e.email,
      label: normalizeLabel(e.label),
      address: e.email,
    })),
    addresses: (c.postalAddresses || []).map((a) => ({
      id: a.label,
      label: normalizeLabel(a.label),
      formatted: a.formattedAddress || [a.street, a.city, a.region, a.state].filter(Boolean).join(", "),
    })),
    thumbnailPath: c.thumbnailPath || undefined,
    hasPhoto: !!c.hasThumbnail,
  };
};

/** Check / request the READ_CONTACTS permission. */
export const requestContactsPermission = async (): Promise<PermState> => {
  try {
    if (Platform.OS === "android") {
      const res = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.READ_CONTACTS,
        {
          title: "callmony",
          message: "Allow callmony to read your contacts so they appear in the app?",
          buttonPositive: "Allow",
          buttonNegative: "Not now",
        }
      );
      return res === PermissionsAndroid.RESULTS.GRANTED ? "authorized" : "denied";
    }
    const res = await Contacts.requestPermission();
    return res === "authorized" ? "authorized" : res === "undefined" ? "undetermined" : "denied";
  } catch {
    return "denied";
  }
};

export const checkContactsPermission = async (): Promise<PermState> => {
  try {
    const res = await Contacts.checkPermission();
    return res === "authorized" ? "authorized" : res === "undefined" ? "undetermined" : "denied";
  } catch {
    return "denied";
  }
};

/** Fetch every phone contact (fast path: no photo bytes). */
export const fetchAllContacts = async (): Promise<DeviceContact[]> => {
  const raw = await Contacts.getAllWithoutPhotos();
  const list = (raw || []).map(toDeviceContact);
  list.sort((a, b) =>
    (a.displayName || "~").localeCompare(b.displayName || "~")
  );
  return list;
};

/* ---------------- disk snapshot ---------------- */

/** Read the cached snapshot — resolves null when there is none. */
export const loadCachedContacts = async (): Promise<ContactsCache | null> => {
  try {
    const txt = await RNFS.readFile(CACHE_FILE, "utf8");
    const parsed = JSON.parse(txt) as ContactsCache;
    if (!parsed || !Array.isArray(parsed.list) || parsed.list.length === 0) return null;
    return parsed;
  } catch {
    return null;
  }
};

let saveTimer: ReturnType<typeof setTimeout> | null = null;

/** Persist the snapshot (debounced — a favorite toggle must not rewrite a
    3 MB file). Runs off the next tick so it can never block first paint. */
export const saveCachedContacts = (list: DeviceContact[]): void => {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    saveTimer = null;
    const payload: ContactsCache = { savedAt: Date.now(), count: list.length, list };
    RNFS.writeFile(CACHE_FILE, JSON.stringify(payload), "utf8").catch(() => undefined);
  }, 1500);
};

/** Drop the snapshot (e.g. permission revoked / wipe). */
export const clearContactsCache = async (): Promise<void> => {
  try {
    await RNFS.unlink(CACHE_FILE);
  } catch {
    /* file may not exist */
  }
};

/** Full photo for one contact (contact detail hero). */
export const fetchContactPhoto = async (recordID: string): Promise<string | null> => {
  try {
    const photo = await Contacts.getPhotoForId(recordID);
    return photo || null;
  } catch {
    return null;
  }
};

/** Open the OS "new contact" form (pre-filled) — used by the keypad. */
export const openNewContactForm = async (prefill: {
  givenName?: string;
  phone?: string;
}): Promise<DeviceContact | null> => {
  try {
    const saved = await Contacts.openContactForm({
      givenName: prefill.givenName || "",
      phoneNumbers: prefill.phone ? [{ label: "mobile", number: prefill.phone }] : [],
    });
    return saved ? toDeviceContact(saved) : null;
  } catch {
    return null;
  }
};

/** Open the OS editor for an existing contact. */
export const openExistingContactEditor = async (recordID: string): Promise<boolean> => {
  try {
    const all = await Contacts.getContactsMatchingString("");
    const target = all.find((c) => c.recordID === recordID);
    if (!target) return false;
    if (Platform.OS === "ios") {
      await Contacts.editExistingContact(target);
    } else {
      await Contacts.openExistingContact(target);
    }
    return true;
  } catch {
    return false;
  }
};

/* ---------------- in-app raw editing (WRITE_CONTACTS) ---------------- */

/** Fetch the RAW react-native-contacts object for one record — the complete
    OS row with every field and internal ID, so an edit mutates ONLY what the
    user changed and updateContact can apply the whole row without dropping
    anything (emails/addresses/birthday/note stay intact). */
export const getRawContactById = async (recordID: string): Promise<Contacts.Contact | null> => {
  try {
    const raw = await Contacts.getContactById(recordID);
    return raw ?? null;
  } catch {
    return null;
  }
};

/** Ensure WRITE_CONTACTS is granted (Android runtime request; iOS reuses the
    single contacts permission the app already asked for when reading). */
const ensureWritePermission = async (): Promise<boolean> => {
  try {
    if (Platform.OS === "android") {
      const res = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.WRITE_CONTACTS,
        {
          title: "callmony",
          message: "Allow callmony to save changes to this contact?",
          buttonPositive: "Allow",
          buttonNegative: "Not now",
        }
      );
      return res === PermissionsAndroid.RESULTS.GRANTED;
    }
    const res = await Contacts.checkPermission();
    return res === "authorized";
  } catch {
    return false;
  }
};

/** Persist a mutated raw contact (must come from getContactById — or be built
    keeping every original field). Requests WRITE_CONTACTS when needed. */
export const updateRawContact = async (raw: Contacts.Contact): Promise<boolean> => {
  try {
    const allowed = await ensureWritePermission();
    if (!allowed) return false;
    await Contacts.updateContact(raw);
    return true;
  } catch {
    return false;
  }
};

/** View a contact in the native contacts app (Android friendly). */
export const viewContactInApp = async (recordID: string): Promise<boolean> => {
  try {
    if (Platform.OS === "android") {
      await Contacts.viewExistingContact({ recordID });
      return true;
    }
    return openExistingContactEditor(recordID);
  } catch {
    return false;
  }
};

export interface DuplicatePair {
  primary: DeviceContact;
  duplicate: DeviceContact;
  reason: "phone" | "name";
}

/** Find duplicate contacts (same normalized phone OR same display name). */
export const findDuplicateContacts = (contacts: DeviceContact[]): DuplicatePair[] => {
  const pairs: DuplicatePair[] = [];
  const used = new Set<string>();
  for (let i = 0; i < contacts.length; i++) {
    for (let j = i + 1; j < contacts.length; j++) {
      const a = contacts[i];
      const b = contacts[j];
      if (used.has(a.recordID) || used.has(b.recordID)) continue;
      const samePhone = a.phones.some((pa) =>
        b.phones.some((pb) => pa.number.replace(/\D/g, "") === pb.number.replace(/\D/g, "") && pa.number.replace(/\D/g, "").length > 5)
      );
      const sameName =
        a.displayName && a.displayName === b.displayName;
      if (samePhone || sameName) {
        pairs.push({ primary: a, duplicate: b, reason: samePhone ? "phone" : "name" });
        used.add(b.recordID);
      }
    }
  }
  return pairs;
};
