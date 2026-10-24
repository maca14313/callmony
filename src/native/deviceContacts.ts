/* Device contacts — react-native-contacts wrapper.
   The address book is loaded ONCE per stale window and kept as a disk
   snapshot (JSON file): the app opens with contacts INSTANTLY from the
   cache — the OS is only queried again when the snapshot is older than
   CONTACTS_TTL (5 min) or after an in-app contact edit. A 5000-contact
   book never re-serializes over the bridge on every open. */

import { NativeModules, PermissionsAndroid, Platform } from "react-native";
import Contacts from "react-native-contacts";
import RNFS from "react-native-fs";
import type { DeviceContact } from "../lib/types";
import { normalizePhone } from "../lib/utils";

export type PermState = "authorized" | "denied" | "undetermined";

/** contacts older than this are re-fetched from the OS (ms). */
export const CONTACTS_TTL = 5 * 60 * 1000;

const CACHE_FILE = `${RNFS.DocumentDirectoryPath}/contacts-cache-v2.json`;
/* v1.6.10 — the v1 cache was written WITHOUT middleName, and its stored
   displayName already lost the middle token. It is still rendered on open
   (instant paint), but marked stale so the boot TTL guard re-fetches the
   book and rewrites the cache with full names; the legacy file is removed
   after the first v2 save. */
const LEGACY_CACHE_FILE = `${RNFS.DocumentDirectoryPath}/contacts-cache.json`;

export interface ContactsCache {
  savedAt: number;
  count: number;
  list: DeviceContact[];
  /** true when the data came from the v1 file (middle names missing) —
      callers should treat the snapshot as STALE and refresh. */
  legacy?: boolean;
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
  /* v1.6.10 — the MIDDLE name is part of the composed display name.
     Dropping it was the "store landlord Work" → "store Work" bug: the
     composed name went into displayName and every list in the app showed
     the shortened form. given+middle+family, whitespace-safe. */
  const middle = c.middleName || "";
  const family = c.familyName || "";
  const composed = `${given} ${middle} ${family}`.replace(/\s+/g, " ").trim();
  return {
    recordID: c.recordID,
    displayName: composed || c.displayName || c.company || "",
    givenName: given,
    middleName: middle || undefined,
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

/** Read the cached snapshot — resolves null when there is none. Falls back
    to the v1 file (marked legacy → stale) until the v2 file exists. */
export const loadCachedContacts = async (): Promise<ContactsCache | null> => {
  try {
    const txt = await RNFS.readFile(CACHE_FILE, "utf8");
    const parsed = JSON.parse(txt) as ContactsCache;
    if (!parsed || !Array.isArray(parsed.list) || parsed.list.length === 0) return null;
    return parsed;
  } catch {
    /* fall through to the legacy file */
  }
  try {
    const txt = await RNFS.readFile(LEGACY_CACHE_FILE, "utf8");
    const parsed = JSON.parse(txt) as ContactsCache;
    if (!parsed || !Array.isArray(parsed.list) || parsed.list.length === 0) return null;
    return { ...parsed, legacy: true };
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
    RNFS.writeFile(CACHE_FILE, JSON.stringify(payload), "utf8")
      .then(() => {
        /* v1.6.10 — the v2 snapshot now exists: drop the legacy file for good */
        RNFS.unlink(LEGACY_CACHE_FILE).catch(() => undefined);
      })
      .catch(() => undefined);
  }, 1500);
};

/** Drop the snapshot (e.g. permission revoked / wipe) — both generations. */
export const clearContactsCache = async (): Promise<void> => {
  try {
    await RNFS.unlink(CACHE_FILE);
  } catch {
    /* file may not exist */
  }
  try {
    await RNFS.unlink(LEGACY_CACHE_FILE);
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

/* ---------------- in-app contact editing (WRITE_CONTACTS) ---------------- */

/* v1.6.1 — editing goes through our OWN ContactEditModule on Android.
   react-native-contacts' updateContact rewrites the MIMETYPE of every data
   row of the contact (corrupting notes/addresses) and scopes its
   delete+re-insert to ONE rawContactId while a contact's numbers can live
   across several raw rows — that mismatch made contacts LOSE phone numbers
   after an in-app edit, and its AsyncTask bridge can die without settling
   the promise (the forever-spinner editor). Our module loads/saves with
   row-id-scoped operations and ALWAYS settles. */

export interface EditablePhone {
  label: string;
  number: string;
}
export interface EditableEmail {
  label: string;
  email: string;
}
export interface ContactEditData {
  recordID: string;
  givenName: string;
  middleName?: string;
  familyName: string;
  company: string;
  jobTitle?: string;
  phoneNumbers: EditablePhone[];
  emailAddresses: EditableEmail[];
}

interface ContactEditNative {
  loadContact(contactId: string): Promise<ContactEditData | null>;
  saveContact(fields: ContactEditData): Promise<boolean>;
  /** v1.6.8 — in-app creation; resolves the NEW contact's recordID. */
  createContact(fields: ContactEditData): Promise<string>;
}

const ContactEdit = NativeModules.CallMonyContactEdit as ContactEditNative | undefined;

/** Load every editable field of one contact — all phones/emails across all
    of the contact's raw rows. Resolves null when not found / unavailable. */
export const loadContactForEdit = async (recordID: string): Promise<ContactEditData | null> => {
  if (Platform.OS === "android" && ContactEdit) {
    try {
      return await ContactEdit.loadContact(recordID);
    } catch {
      return null;
    }
  }
  /* iOS — react-native-contacts' own editor is safe there */
  try {
    const raw = await Contacts.getContactById(recordID);
    if (!raw) return null;
    return {
      recordID: raw.recordID,
      givenName: raw.givenName || "",
      middleName: raw.middleName || "",
      familyName: raw.familyName || "",
      company: raw.company || "",
      jobTitle: raw.jobTitle || "",
      phoneNumbers: (raw.phoneNumbers || []).map((p) => ({ label: normalizeLabel(p.label), number: p.number })),
      emailAddresses: (raw.emailAddresses || []).map((e) => ({ label: normalizeLabel(e.label), email: e.email })),
    };
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

/** Persist the edited fields (name/company/phones/emails). ONLY those —
    note, birthday, addresses and photos are never touched. */
export const saveContactEdit = async (data: ContactEditData): Promise<boolean> => {
  const allowed = await ensureWritePermission();
  if (!allowed) return false;
  if (Platform.OS === "android" && ContactEdit) {
    try {
      await ContactEdit.saveContact(data);
      return true;
    } catch {
      return false;
    }
  }
  /* iOS — mutate the freshly loaded raw row so nothing else is dropped */
  try {
    const raw = await Contacts.getContactById(data.recordID);
    if (!raw) return false;
    raw.givenName = data.givenName;
    raw.middleName = data.middleName ?? "";
    raw.familyName = data.familyName;
    raw.company = data.company;
    raw.jobTitle = data.jobTitle ?? "";
    raw.phoneNumbers = data.phoneNumbers.map((p) => ({ label: p.label, number: p.number }));
    raw.emailAddresses = data.emailAddresses.map((e) => ({ label: e.label, email: e.email }));
    await Contacts.updateContact(raw);
    return true;
  } catch {
    return false;
  }
};

/* ---------------- in-app contact creation (v1.6.8) ---------------- */

/** Create a contact ENTIRELY in-app (no OS editor app). Resolves the new
    recordID, or null on failure/denied. */
export const createDeviceContact = async (data: {
  givenName?: string;
  middleName?: string;
  familyName?: string;
  company?: string;
  jobTitle?: string;
  phoneNumbers: EditablePhone[];
  emailAddresses?: EditableEmail[];
}): Promise<string | null> => {
  const allowed = await ensureWritePermission();
  if (!allowed) return null;
  const fields: ContactEditData = {
    recordID: "",
    givenName: data.givenName ?? "",
    middleName: data.middleName ?? "",
    familyName: data.familyName ?? "",
    company: data.company ?? "",
    jobTitle: data.jobTitle ?? "",
    phoneNumbers: data.phoneNumbers,
    emailAddresses: data.emailAddresses ?? [],
  };
  if (Platform.OS === "android" && ContactEdit) {
    try {
      return (await ContactEdit.createContact(fields)) || null;
    } catch {
      return null;
    }
  }
  /* iOS — react-native-contacts' addContact is a pure insert (safe) */
  try {
    const raw: Contacts.Contact = {
      recordID: "",
      givenName: data.givenName ?? "",
      middleName: data.middleName ?? "",
      familyName: data.familyName ?? "",
      company: data.company ?? "",
      jobTitle: data.jobTitle ?? "",
      phoneNumbers: data.phoneNumbers.map((p) => ({ label: p.label, number: p.number })),
      emailAddresses: (data.emailAddresses ?? []).map((e) => ({ label: e.label, email: e.email })),
    } as Contacts.Contact;
    const saved = await Contacts.addContact(raw);
    return saved?.recordID || null;
  } catch {
    return null;
  }
};

/** Append one phone number to an EXISTING contact, in-app, loss-free:
    loads the live OS row, unions in the new number (skips when the contact
    already has it), saves back through the same surgical editor. Returns
    "added" | "duplicate" | "failed". */
export const addPhoneToContact = async (
  recordID: string,
  number: string,
  label = "mobile"
): Promise<"added" | "duplicate" | "failed"> => {
  try {
    const data = await loadContactForEdit(recordID);
    if (!data) return "failed";
    const have = new Set(
      data.phoneNumbers.map((p) => normalizePhone(p.number).replace(/\D/g, ""))
    );
    const key = normalizePhone(number).replace(/\D/g, "");
    if (key && have.has(key)) return "duplicate";
    data.phoneNumbers = [...data.phoneNumbers, { label, number }];
    const ok = await saveContactEdit(data);
    return ok ? "added" : "failed";
  } catch {
    return "failed";
  }
};

/** Delete a contact from the OS address book (merge-duplicates flow). */
export const deleteDeviceContact = async (recordID: string): Promise<boolean> => {
  try {
    await Contacts.deleteContact({ recordID } as Contacts.Contact);
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
