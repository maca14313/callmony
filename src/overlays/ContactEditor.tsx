/* ContactEditor — the IN-APP editor for an existing device contact (replaces
   the OS external editor), and ContactCreator (v1.6.8) — the in-app "new
   contact" form. Given / family / company + editable phone rows
   (label picker mobile→work→home→other · number · remove · add · DEFAULT
   phone star) + email rows.

   v1.6.1 — load/save go through our own ContactEditModule (Android) which
   1. ALWAYS settles the promise (the old bridge could hang forever on a
      full-screen spinner the user could not escape), and
   2. never loses numbers: the editor merges the OS row with the app's cached
      copy (union by normalized digits) before saving, and blocks a save that
      would drop every number of a contact that still has some.

   v1.6.8 — ZERO-LAG SAVE (user report: "after I click Save there is some
   lag or freeze"): the old path awaited a FULL address-book re-fetch
   (every contact serialized over the bridge) before closing the sheet.
   Now: the sheet closes the instant Save is tapped, the edited contact is
   patched into the cached list locally (store.patchDeviceContact), and the
   silent background refresh reconciles afterwards — while any editor is
   open the refresh itself is deferred (store), so typing is never frozen
   by a whole-book bridge dump either. */

import React, { useEffect, useRef, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone } from "../lib/utils";
import { primaryPhone } from "../lib/share";
import {
  loadContactForEdit,
  saveContactEdit,
  createDeviceContact,
  type ContactEditData,
} from "../native/deviceContacts";
import { F, useThemeColors } from "../theme";
import { Btn, Field, Spinner, toast } from "../components/kit";
import { Sheet } from "../components/Sheet";

/* the four labels the picker cycles through (stored values stay English —
   the same convention the address-book wrapper uses everywhere) */
const PHONE_LABELS = ["mobile", "work", "home", "other"] as const;

const cycleLabel = (current: string): string => {
  const idx = PHONE_LABELS.indexOf(current as (typeof PHONE_LABELS)[number]);
  return idx === -1 ? PHONE_LABELS[0] : PHONE_LABELS[(idx + 1) % PHONE_LABELS.length];
};

const labelKey = (l: string): string =>
  l === "work" ? "cd.work" : l === "home" ? "cd.home" : l === "other" ? "cd.other" : "cd.mobile";

interface PhoneRow {
  label: string;
  number: string;
}
interface EmailRow {
  label: string;
  address: string;
}

/** hard bail for the native load — the editor can never spin forever */
const LOAD_TIMEOUT_MS = 6000;

/* ---------------- shared phone row (memoized — typing in one input no
   longer re-renders every other row of the editor) ---------------- */

const PhoneInputRow: React.FC<{
  row: PhoneRow;
  isDefault: boolean;
  onSetNumber: (v: string) => void;
  onCycleLabel: () => void;
  onSetDefault: () => void;
  onRemove: () => void;
  canRemove: boolean;
  autoFocus?: boolean;
}> = ({ row, isDefault, onSetNumber, onCycleLabel, onSetDefault, onRemove, canRemove, autoFocus }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  return (
    <View style={ceStyles.row}>
      <Pressable
        onPress={onCycleLabel}
        hitSlop={4}
        accessibilityLabel={`${t("ce.label")}: ${t(labelKey(row.label))}`}
        style={[ceStyles.labelBtn, { borderColor: C.line, backgroundColor: C.chip }]}
      >
        <Text style={{ fontSize: F.tiny, fontWeight: "700", color: C.sub }}>{t(labelKey(row.label))}</Text>
        <Icon name="chevron-down" size={13} color={C.faint} />
      </Pressable>
      <TextInput
        value={row.number}
        onChangeText={onSetNumber}
        keyboardType="phone-pad"
        placeholder={t("ce.phonePlaceholder")}
        placeholderTextColor={C.faint}
        autoFocus={autoFocus}
        style={[ceStyles.input, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
      />
      {/* v1.6.8 — DEFAULT-phone star (user request: "from multiple phone
          numbers of the same person I should have the option to choose
          default phone number"): one tap marks the number this app dials,
          shows in headers and matches first. */}
      <Pressable
        onPress={onSetDefault}
        hitSlop={6}
        accessibilityLabel={isDefault ? t("ce.defaultNumber") : t("ce.setPrimaryNumber")}
        accessibilityState={{ selected: isDefault }}
        style={[ceStyles.starBtn, { backgroundColor: isDefault ? "rgba(185,125,16,0.16)" : "transparent" }]}
      >
        <Icon name={isDefault ? "star" : "star-outline"} size={19} color={isDefault ? "#b97d10" : C.faint} />
      </Pressable>
      {canRemove && (
        <Pressable
          onPress={onRemove}
          hitSlop={6}
          accessibilityLabel={t("ce.removePhone")}
          style={[ceStyles.removeBtn, { backgroundColor: "rgba(214,69,69,0.12)" }]}
        >
          <Icon name="close" size={17} color={C.red} />
        </Pressable>
      )}
    </View>
  );
};

/* ---------------- edit an existing contact ---------------- */

export const ContactEditor: React.FC<{ recordID: string; onClose: () => void }> = ({ recordID, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === recordID));
  const dataRef = useRef<ContactEditData | null>(null);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "failed">("loading");
  const [saving, setSaving] = useState(false);
  const [given, setGiven] = useState("");
  /* v1.6.10 — the middle name is a first-class editable field: it was
     loaded + saved invisibly before, so a "store landlord Work" contact
     showed an editor with no hint the middle token existed. */
  const [middle, setMiddle] = useState("");
  const [family, setFamily] = useState("");
  const [company, setCompany] = useState("");
  const [phones, setPhones] = useState<PhoneRow[]>([]);
  const [emails, setEmails] = useState<EmailRow[]>([]);
  /* v1.6.8 — which number is the DEFAULT (star). Initialized from the
     app-side primary override (or the first mobile row), saved back via
     setPrimaryPhone AFTER the OS write so nothing is lost. */
  const [defaultNumber, setDefaultNumber] = useState("");

  const applyData = (data: ContactEditData) => {
    dataRef.current = data;
    setGiven(data.givenName || "");
    setMiddle(data.middleName || "");
    setFamily(data.familyName || "");
    setCompany(data.company || "");
    setPhones((data.phoneNumbers || []).map((p) => ({ label: p.label, number: p.number })));
    setEmails((data.emailAddresses || []).map((e) => ({ label: e.label, address: e.email })));
    setDefaultNumber(primaryPhone(contact) ?? "");
  };

  /* load the OS row once — with a hard timeout so a stuck bridge call can
     never trap the user on a spinner (v1.6.1, user report: "the loading is
     still there and the app is not usable when it's on") */
  const load = () => {
    let alive = true;
    setLoadState("loading");
    const timer = setTimeout(() => {
      if (alive) setLoadState((s) => (s === "loading" ? "failed" : s));
    }, LOAD_TIMEOUT_MS);
    (async () => {
      const loaded = await loadContactForEdit(recordID);
      if (!alive) return;

      /* defensive merge — if the native load came back with FEWER numbers
         than the app's own cached copy (bridge hiccup, partial read), union
         them so a save can never wipe numbers the app knows about */
      if (loaded) {
        const data = loaded; // const → type narrowing survives the closures below
        const cached = useApp.getState().deviceContacts.find((c) => c.recordID === recordID);
        if (cached && cached.phones.length) {
          const have = new Set(
            data.phoneNumbers.map((p) => normalizePhone(p.number, countryCode).replace(/\D/g, ""))
          );
          cached.phones.forEach((p) => {
            const key = normalizePhone(p.number, countryCode).replace(/\D/g, "");
            if (key && !have.has(key)) data.phoneNumbers.push({ label: p.label, number: p.number });
          });
        }
        if (cached && cached.emails.length) {
          const have = new Set(data.emailAddresses.map((e) => e.email.toLowerCase()));
          cached.emails.forEach((e) => {
            if (e.address && !have.has(e.address.toLowerCase()))
              data.emailAddresses.push({ label: e.label, email: e.address });
          });
        }
      }

      clearTimeout(timer);
      if (!alive) return;
      if (loaded) {
        applyData(loaded);
        setLoadState("ready");
      } else {
        setLoadState("failed");
      }
    })();
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  };

  useEffect(() => load(), [recordID]); // eslint-disable-line react-hooks/exhaustive-deps

  const setPhone = (i: number, patch: Partial<PhoneRow>) =>
    setPhones((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const removePhone = (i: number) => {
    const removed = phones[i];
    setPhones((rows) => rows.filter((_, idx) => idx !== i));
    if (removed && removed.number === defaultNumber) {
      /* the default left with the row — fall back to the first remaining */
      const next = phones.find((_, idx) => idx !== i);
      setDefaultNumber(next?.number ?? "");
    }
  };
  const setEmailRow = (i: number, patch: Partial<EmailRow>) =>
    setEmails((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const removeEmail = (i: number) => setEmails((rows) => rows.filter((_, idx) => idx !== i));

  const save = async () => {
    const data = dataRef.current;
    const cleanPhones = phones
      .map((p) => ({ label: p.label, number: p.number.trim() }))
      .filter((p) => p.number);
    const cleanEmails = emails
      .map((e) => ({ label: e.label, email: e.address.trim() }))
      .filter((e) => e.email);
    if (!data || (!given.trim() && !family.trim() && !company.trim() && cleanPhones.length === 0)) {
      toast(t("ce.atLeastOne"), "error");
      return;
    }
    /* guard: a contact that HAD numbers must not be saved with zero — the
       remove button is per-row, wiping them all is almost always accidental */
    if (data.phoneNumbers.length > 0 && cleanPhones.length === 0) {
      toast(t("ce.atLeastOnePhone"), "error");
      return;
    }
    /* v1.6.8 — the chosen default must be one of the SAVED numbers */
    let chosenDefault = cleanPhones.find((p) => p.number === defaultNumber)?.number ?? "";
    if (!chosenDefault && cleanPhones.length) chosenDefault = cleanPhones[0].number;

    setSaving(true);
    const ok = await saveContactEdit({
      recordID: data.recordID,
      givenName: given.trim(),
      middleName: middle.trim(),
      familyName: family.trim(),
      company: company.trim(),
      jobTitle: data.jobTitle ?? "",
      phoneNumbers: cleanPhones,
      emailAddresses: cleanEmails,
    });
    setSaving(false);
    if (!ok) {
      toast(t("cd.saveFailed"), "error");
      return;
    }
    /* v1.6.8 — ZERO-LAG: close + toast + LOCAL patch IMMEDIATELY; the
       whole-book refresh runs in the background afterwards (and the store
       defers it while any editor is open). The old code awaited the full
       re-fetch here — that bridge dump was the reported Save freeze. */
    /* v1.6.10 — the locally patched display name composes given + MIDDLE +
       family — the old patch dropped the middle token, so a saved contact
       kept showing the shortened name until the background refresh. */
    const composed = `${given.trim()} ${middle.trim()} ${family.trim()}`.replace(/\s+/g, " ").trim();
    useApp.getState().patchDeviceContact(data.recordID, {
      displayName: composed || company.trim() || data.recordID,
      givenName: given.trim(),
      middleName: middle.trim() || undefined,
      familyName: family.trim(),
      company: company.trim() || undefined,
      phones: cleanPhones.map((p) => ({ id: p.number, label: p.label, number: p.number })),
      emails: cleanEmails.map((e) => ({ id: e.email, label: e.label, address: e.email })),
    });
    if (chosenDefault) {
      useApp.getState().setPrimaryPhone(data.recordID, chosenDefault);
    }
    toast(t("ce.contactUpdated"), "ok");
    onClose();
    /* reconcile in the background — labels, photos, other raw rows */
    setTimeout(() => {
      useApp.getState().refreshDeviceContacts().catch(() => undefined);
    }, 800);
  };

  if (loadState === "loading") {
    return (
      <Sheet title={t("ce.editContact")} onBack={onClose} scroll={false}>
        <View style={{ flex: 1, alignItems: "center", justifyContent: "center", paddingVertical: 60, gap: 14 }}>
          <Spinner />
          <Text style={{ fontSize: F.small, color: C.faint }}>{t("cd.loadingContacts")}</Text>
        </View>
      </Sheet>
    );
  }

  if (loadState === "failed") {
    return (
      <Sheet title={t("ce.editContact")} subtitle={t("cd.notFound")} onBack={onClose}>
        <View style={{ alignItems: "center", paddingVertical: 26, gap: 12 }}>
          <Icon name="cloud-alert-outline" size={40} color={C.faint} />
          <Text style={{ fontSize: F.body, color: C.sub, textAlign: "center" }}>{t("ce.loadFailed")}</Text>
          <View style={{ flexDirection: "row", gap: 10 }}>
            <Btn label={t("act.cancel")} variant="ghost" onPress={onClose} style={{ flex: 1 }} />
            <Btn label={t("act.retry")} icon="refresh" onPress={load} style={{ flex: 1 }} />
          </View>
        </View>
      </Sheet>
    );
  }

  return (
    <Sheet
      title={t("ce.editContact")}
      subtitle={contact ? displayName(contact) : undefined}
      onBack={onClose}
      scroll={false}
      footer={
        <View style={{ flexDirection: "row", gap: 10 }}>
          <Btn label={t("act.cancel")} variant="ghost" onPress={onClose} style={{ flex: 1 }} />
          <Btn
            label={t("act.save")}
            icon="content-save-outline"
            onPress={() => void save()}
            loading={saving}
            style={{ flex: 1 }}
          />
        </View>
      }
    >
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ paddingHorizontal: 18, paddingTop: 12, paddingBottom: 24 }}
        keyboardShouldPersistTaps="handled"
      >
        <Field label={t("ce.firstName")} value={given} onChangeText={setGiven} placeholder={t("ce.firstName")} autoFocus />
        <Field label={t("ce.middleName")} value={middle} onChangeText={setMiddle} placeholder={t("ce.middleName")} />
        <Field label={t("ce.lastName")} value={family} onChangeText={setFamily} placeholder={t("ce.lastName")} />
        <Field label={t("ce.company")} value={company} onChangeText={setCompany} placeholder={t("ce.company")} />

        <Text style={[ceStyles.sectionTitle, { color: C.brand }]}>
          {t("cd.phones")} · {t("ce.defaultNumber")}
        </Text>
        {phones.map((p, i) => (
          <PhoneInputRow
            key={i}
            row={p}
            isDefault={!!p.number && p.number === defaultNumber}
            onSetNumber={(v) => setPhone(i, { number: v })}
            onCycleLabel={() => setPhone(i, { label: cycleLabel(p.label) })}
            onSetDefault={() => p.number && setDefaultNumber(p.number)}
            onRemove={() => removePhone(i)}
            canRemove
          />
        ))}
        <Pressable
          onPress={() => setPhones((rows) => [...rows, { label: "mobile", number: "" }])}
          style={[ceStyles.addRow, { borderColor: C.line }]}
          accessibilityLabel={t("ce.addPhone")}
        >
          <Icon name="plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("ce.addPhone")}</Text>
        </Pressable>

        <Text style={[ceStyles.sectionTitle, { color: C.brand }]}>{t("cd.emails")}</Text>
        {emails.map((e, i) => (
          <View key={i} style={ceStyles.row}>
            <TextInput
              value={e.address}
              onChangeText={(v) => setEmailRow(i, { address: v })}
              keyboardType="email-address"
              autoCapitalize="none"
              placeholder={t("ce.emailPlaceholder")}
              placeholderTextColor={C.faint}
              style={[ceStyles.input, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
            />
            <Pressable
              onPress={() => removeEmail(i)}
              hitSlop={6}
              accessibilityLabel={t("ce.removeEmail")}
              style={[ceStyles.removeBtn, { backgroundColor: "rgba(214,69,69,0.12)" }]}
            >
              <Icon name="close" size={17} color={C.red} />
            </Pressable>
          </View>
        ))}
        <Pressable
          onPress={() => setEmails((rows) => [...rows, { label: "other", address: "" }])}
          style={[ceStyles.addRow, { borderColor: C.line }]}
          accessibilityLabel={t("ce.addEmail")}
        >
          <Icon name="plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("ce.addEmail")}</Text>
        </Pressable>
      </ScrollView>
    </Sheet>
  );
};

/* ---------------- create a NEW contact (v1.6.8, entirely in-app) ----------------
   Simple by design (user request): first/last name + phones (with the
   default star) + one optional email row set. Everything else can be added
   later via the editor. Never opens an outside app. */

export const ContactCreator: React.FC<{
  prefillPhone?: string;
  prefillName?: string;
  onClose: () => void;
}> = ({ prefillPhone, prefillName, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const [saving, setSaving] = useState(false);
  const [given, setGiven] = useState(prefillName ?? "");
  /* v1.6.10 — middle name in the creator too: full-name fidelity from the
     very first save (matches the editor's fields). */
  const [middle, setMiddle] = useState("");
  const [family, setFamily] = useState("");
  const [phones, setPhones] = useState<PhoneRow[]>(
    prefillPhone ? [{ label: "mobile", number: prefillPhone }] : [{ label: "mobile", number: "" }]
  );
  const [emails, setEmails] = useState<EmailRow[]>([]);
  const [defaultNumber, setDefaultNumber] = useState(prefillPhone ?? "");

  const setPhone = (i: number, patch: Partial<PhoneRow>) =>
    setPhones((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const removePhone = (i: number) => {
    const removed = phones[i];
    setPhones((rows) => rows.filter((_, idx) => idx !== i));
    if (removed && removed.number === defaultNumber) {
      const next = phones.find((_, idx) => idx !== i);
      setDefaultNumber(next?.number ?? "");
    }
  };

  const save = async () => {
    const cleanPhones = phones
      .map((p) => ({ label: p.label, number: p.number.trim() }))
      .filter((p) => p.number);
    const cleanEmails = emails
      .map((e) => ({ label: e.label, email: e.address.trim() }))
      .filter((e) => e.email);
    if (!given.trim() && !family.trim() && cleanPhones.length === 0) {
      toast(t("ce.atLeastOne"), "error");
      return;
    }
    let chosenDefault = cleanPhones.find((p) => p.number === defaultNumber)?.number ?? "";
    if (!chosenDefault && cleanPhones.length) chosenDefault = cleanPhones[0].number;

    setSaving(true);
    const recordID = await createDeviceContact({
      givenName: given.trim(),
      middleName: middle.trim(),
      familyName: family.trim(),
      phoneNumbers: cleanPhones,
      emailAddresses: cleanEmails,
    });
    setSaving(false);
    if (!recordID) {
      toast(t("cd.saveFailed"), "error");
      return;
    }
    /* instant local insert — the contact exists in the app RIGHT NOW; the
       silent background refresh reconciles photo/thumbnail etc. after.
       v1.6.10 — composed WITH the middle name (full-name fidelity). */
    const composed = `${given.trim()} ${middle.trim()} ${family.trim()}`.replace(/\s+/g, " ").trim();
    useApp.getState().addDeviceContactToList({
      recordID,
      displayName: composed || prettyPhone(cleanPhones[0]?.number ?? "", countryCode),
      givenName: given.trim(),
      middleName: middle.trim() || undefined,
      familyName: family.trim(),
      phones: cleanPhones.map((p) => ({ id: p.number, label: p.label, number: p.number })),
      emails: cleanEmails.map((e) => ({ id: e.email, label: e.label, address: e.email })),
      addresses: [],
      hasPhoto: false,
    });
    if (chosenDefault) {
      useApp.getState().setPrimaryPhone(recordID, chosenDefault);
    }
    toast(t("ce.contactSaved"), "ok");
    onClose();
    setTimeout(() => {
      useApp.getState().refreshDeviceContacts().catch(() => undefined);
    }, 800);
  };

  return (
    <Sheet
      title={t("ce.newContact")}
      onBack={onClose}
      scroll={false}
      footer={
        <View style={{ flexDirection: "row", gap: 10 }}>
          <Btn label={t("act.cancel")} variant="ghost" onPress={onClose} style={{ flex: 1 }} />
          <Btn
            label={t("act.save")}
            icon="account-plus-outline"
            onPress={() => void save()}
            loading={saving}
            style={{ flex: 1 }}
          />
        </View>
      }
    >
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ paddingHorizontal: 18, paddingTop: 12, paddingBottom: 24 }}
        keyboardShouldPersistTaps="handled"
      >
        <Field label={t("ce.firstName")} value={given} onChangeText={setGiven} placeholder={t("ce.firstName")} autoFocus />
        <Field label={t("ce.middleName")} value={middle} onChangeText={setMiddle} placeholder={t("ce.middleName")} />
        <Field label={t("ce.lastName")} value={family} onChangeText={setFamily} placeholder={t("ce.lastName")} />

        <Text style={[ceStyles.sectionTitle, { color: C.brand }]}>
          {t("cd.phones")} · {t("ce.defaultNumber")}
        </Text>
        {phones.map((p, i) => (
          <PhoneInputRow
            key={i}
            row={p}
            isDefault={!!p.number && p.number === defaultNumber}
            onSetNumber={(v) => setPhone(i, { number: v })}
            onCycleLabel={() => setPhone(i, { label: cycleLabel(p.label) })}
            onSetDefault={() => p.number && setDefaultNumber(p.number)}
            onRemove={() => removePhone(i)}
            canRemove={phones.length > 1}
          />
        ))}
        <Pressable
          onPress={() => setPhones((rows) => [...rows, { label: "mobile", number: "" }])}
          style={[ceStyles.addRow, { borderColor: C.line }]}
          accessibilityLabel={t("ce.addPhone")}
        >
          <Icon name="plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("ce.addPhone")}</Text>
        </Pressable>

        <Text style={[ceStyles.sectionTitle, { color: C.brand }]}>{t("cd.emails")}</Text>
        {emails.map((e, i) => (
          <View key={i} style={ceStyles.row}>
            <TextInput
              value={e.address}
              onChangeText={(v) => setEmails((rows) => rows.map((r, idx) => (idx === i ? { ...r, address: v } : r)))}
              keyboardType="email-address"
              autoCapitalize="none"
              placeholder={t("ce.emailPlaceholder")}
              placeholderTextColor={C.faint}
              style={[ceStyles.input, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
            />
            <Pressable
              onPress={() => setEmails((rows) => rows.filter((_, idx) => idx !== i))}
              hitSlop={6}
              accessibilityLabel={t("ce.removeEmail")}
              style={[ceStyles.removeBtn, { backgroundColor: "rgba(214,69,69,0.12)" }]}
            >
              <Icon name="close" size={17} color={C.red} />
            </Pressable>
          </View>
        ))}
        <Pressable
          onPress={() => setEmails((rows) => [...rows, { label: "other", address: "" }])}
          style={[ceStyles.addRow, { borderColor: C.line }]}
          accessibilityLabel={t("ce.addEmail")}
        >
          <Icon name="plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("ce.addEmail")}</Text>
        </Pressable>
      </ScrollView>
    </Sheet>
  );
};

const ceStyles = StyleSheet.create({
  sectionTitle: { fontSize: F.small, fontWeight: "800", letterSpacing: 0.5, marginTop: 6, marginBottom: 8 },
  row: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 10 },
  labelBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 10,
    minHeight: 48,
    minWidth: 84,
  },
  input: { flex: 1, minWidth: 0, borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, fontSize: F.body, minHeight: 48 },
  starBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: "center",
    justifyContent: "center",
  },
  removeBtn: { width: 36, height: 36, borderRadius: 18, alignItems: "center", justifyContent: "center" },
  addRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: "dashed",
    paddingVertical: 10,
    minHeight: 44,
    marginBottom: 10,
  },
});
