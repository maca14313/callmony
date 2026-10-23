/* ContactEditor — the IN-APP editor for an existing device contact (replaces
   the OS external editor). Given / family / company + editable phone rows
   (label picker mobile→work→home→other · number · remove · add) + email rows.
   It loads the RAW react-native-contacts row (every field and internal ID
   kept), mutates ONLY the fields the user edited, then updateContact applies
   the whole row — note, birthday, addresses and every other OS field survive
   the edit untouched. */

import React, { useEffect, useRef, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import Contacts from "react-native-contacts";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import type { DeviceContact } from "../lib/types";
import { getRawContactById, updateRawContact } from "../native/deviceContacts";
import { displayName } from "../lib/utils";
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

/** Best-effort RAW row built from the app's DeviceContact map — only used
   when getContactById comes back null (bridge hiccup). Keeps every field
   the app knows about so a save never wipes what it cannot see. */
const rawFromDevice = (c: DeviceContact): Contacts.Contact => {
  const m = c.birthday ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(c.birthday) : null;
  return {
    recordID: c.recordID,
    backTitle: "",
    company: c.company ?? "",
    displayName: c.displayName,
    familyName: c.familyName || "",
    givenName: c.givenName || "",
    middleName: "",
    jobTitle: c.jobTitle ?? "",
    phoneNumbers: c.phones.map((p) => ({ label: p.label, number: p.number })),
    hasThumbnail: !!c.hasPhoto,
    thumbnailPath: c.thumbnailPath ?? "",
    isStarred: false,
    postalAddresses: c.addresses.map((a) => ({
      label: a.label,
      formattedAddress: a.formatted,
      street: "",
      pobox: "",
      neighborhood: "",
      city: "",
      region: "",
      state: "",
      postCode: "",
      country: "",
    })),
    prefix: "",
    suffix: "",
    department: "",
    birthday: m
      ? { year: Number(m[1]), month: Number(m[2]), day: Number(m[3]) }
      : { year: 0, month: 0, day: 0 },
    imAddresses: [],
    urlAddresses: [],
    note: c.note ?? "",
    emailAddresses: c.emails.map((e) => ({ label: e.label, email: e.address })),
  };
};

export const ContactEditor: React.FC<{ recordID: string; onClose: () => void }> = ({ recordID, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === recordID));
  const rawRef = useRef<Contacts.Contact | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [given, setGiven] = useState("");
  const [family, setFamily] = useState("");
  const [company, setCompany] = useState("");
  const [phones, setPhones] = useState<PhoneRow[]>([]);
  const [emails, setEmails] = useState<EmailRow[]>([]);

  /* load the raw OS row once (fallback: the app's DeviceContact map) */
  useEffect(() => {
    let alive = true;
    (async () => {
      const raw = await getRawContactById(recordID);
      if (!alive) return;
      const fallback = useApp.getState().deviceContacts.find((c) => c.recordID === recordID);
      const base = raw ?? (fallback ? rawFromDevice(fallback) : null);
      if (base) {
        rawRef.current = base;
        setGiven(base.givenName || "");
        setFamily(base.familyName || "");
        setCompany(base.company || "");
        setPhones((base.phoneNumbers || []).map((p) => ({ label: p.label, number: p.number })));
        setEmails((base.emailAddresses || []).map((e) => ({ label: e.label, address: e.email })));
      }
      setLoading(false);
    })();
    return () => {
      alive = false;
    };
  }, [recordID]);

  const setPhone = (i: number, patch: Partial<PhoneRow>) =>
    setPhones((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const removePhone = (i: number) => setPhones((rows) => rows.filter((_, idx) => idx !== i));
  const setEmailRow = (i: number, patch: Partial<EmailRow>) =>
    setEmails((rows) => rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));
  const removeEmail = (i: number) => setEmails((rows) => rows.filter((_, idx) => idx !== i));

  const save = async () => {
    const raw = rawRef.current;
    const cleanPhones = phones
      .map((p) => ({ label: p.label, number: p.number.trim() }))
      .filter((p) => p.number);
    const cleanEmails = emails
      .map((e) => ({ label: e.label, email: e.address.trim() }))
      .filter((e) => e.email);
    if (!raw || (!given.trim() && !family.trim() && !company.trim() && cleanPhones.length === 0)) {
      toast(t("ce.atLeastOne"), "error");
      return;
    }
    /* mutate ONLY the edited fields — every other property of the raw OS
       row (note, birthday, addresses, URLs, …) stays exactly as loaded */
    raw.givenName = given.trim();
    raw.familyName = family.trim();
    raw.company = company.trim();
    raw.phoneNumbers = cleanPhones;
    raw.emailAddresses = cleanEmails;
    setSaving(true);
    const ok = await updateRawContact(raw);
    setSaving(false);
    if (!ok) {
      toast(t("cd.saveFailed"), "error");
      return;
    }
    await useApp.getState().refreshDeviceContacts(); // pick up the edit at once
    toast(t("ce.contactUpdated"), "ok");
    onClose();
  };

  if (loading) {
    return (
      <Sheet title={t("ce.editContact")} onBack={onClose} scroll={false}>
        <View style={{ flex: 1, alignItems: "center", justifyContent: "center", paddingVertical: 60 }}>
          <Spinner />
        </View>
      </Sheet>
    );
  }

  if (!rawRef.current) {
    return (
      <Sheet title={t("ce.editContact")} subtitle={t("cd.notFound")} onBack={onClose}>
        <Text style={{ color: C.sub, fontSize: F.body, textAlign: "center", paddingVertical: 30 }}>
          {t("cd.notFoundHint")}
        </Text>
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
            disabled={saving}
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
