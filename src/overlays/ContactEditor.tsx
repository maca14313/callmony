/* ContactEditor — the IN-APP editor for an existing device contact (replaces
   the OS external editor). Given / family / company + editable phone rows
   (label picker mobile→work→home→other · number · remove · add) + email rows.

   v1.6.1 — load/save go through our own ContactEditModule (Android) which
   1. ALWAYS settles the promise (the old bridge could hang forever on a
      full-screen spinner the user could not escape), and
   2. never loses numbers: the editor merges the OS row with the app's cached
      copy (union by normalized digits) before saving, and blocks a save that
      would drop every number of a contact that still has some. */

import React, { useEffect, useRef, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { displayName, normalizePhone } from "../lib/utils";
import {
  loadContactForEdit,
  saveContactEdit,
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

export const ContactEditor: React.FC<{ recordID: string; onClose: () => void }> = ({ recordID, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === recordID));
  const dataRef = useRef<ContactEditData | null>(null);
  const [loadState, setLoadState] = useState<"loading" | "ready" | "failed">("loading");
  const [saving, setSaving] = useState(false);
  const [given, setGiven] = useState("");
  const [family, setFamily] = useState("");
  const [company, setCompany] = useState("");
  const [phones, setPhones] = useState<PhoneRow[]>([]);
  const [emails, setEmails] = useState<EmailRow[]>([]);

  const applyData = (data: ContactEditData) => {
    dataRef.current = data;
    setGiven(data.givenName || "");
    setFamily(data.familyName || "");
    setCompany(data.company || "");
    setPhones((data.phoneNumbers || []).map((p) => ({ label: p.label, number: p.number })));
    setEmails((data.emailAddresses || []).map((e) => ({ label: e.label, address: e.email })));
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
  const removePhone = (i: number) => setPhones((rows) => rows.filter((_, idx) => idx !== i));
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
    setSaving(true);
    const ok = await saveContactEdit({
      recordID: data.recordID,
      givenName: given.trim(),
      middleName: data.middleName ?? "",
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
    await useApp.getState().refreshDeviceContacts(); // pick up the edit at once
    toast(t("ce.contactUpdated"), "ok");
    onClose();
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
        <Field label={t("ce.lastName")} value={family} onChangeText={setFamily} placeholder={t("ce.lastName")} />
        <Field label={t("ce.company")} value={company} onChangeText={setCompany} placeholder={t("ce.company")} />

        <Text style={[ceStyles.sectionTitle, { color: C.brand }]}>{t("cd.phones")}</Text>
        {phones.map((p, i) => (
          <View key={i} style={ceStyles.row}>
            <Pressable
              onPress={() => setPhone(i, { label: cycleLabel(p.label) })}
              hitSlop={4}
              accessibilityLabel={`${t("ce.label")}: ${t(labelKey(p.label))}`}
              style={[ceStyles.labelBtn, { borderColor: C.line, backgroundColor: C.chip }]}
            >
              <Text style={{ fontSize: F.tiny, fontWeight: "700", color: C.sub }}>{t(labelKey(p.label))}</Text>
              <Icon name="chevron-down" size={13} color={C.faint} />
            </Pressable>
            <TextInput
              value={p.number}
              onChangeText={(v) => setPhone(i, { number: v })}
              keyboardType="phone-pad"
              placeholder={t("ce.phonePlaceholder")}
              placeholderTextColor={C.faint}
              style={[ceStyles.input, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
            />
            <Pressable
              onPress={() => removePhone(i)}
              hitSlop={6}
              accessibilityLabel={t("ce.removePhone")}
              style={[ceStyles.removeBtn, { backgroundColor: "rgba(214,69,69,0.12)" }]}
            >
              <Icon name="close" size={17} color={C.red} />
            </Pressable>
          </View>
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
