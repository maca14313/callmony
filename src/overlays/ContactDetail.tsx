/* ContactDetail — ledger-first contact sheet with quick actions and
   segmented tabs (Ledger · Lists · Notes · Bank · Reminders · Info). */

import React, { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { ownerFullBalance, openListsPending } from "../lib/calc";
import type { DeviceContact } from "../lib/types";
import { displayName, fd, money, prettyPhone } from "../lib/utils";
import { contactOwnerId } from "../lib/types";
import { placeCall } from "../native/calls";
import { openExistingContactEditor } from "../native/deviceContacts";
import { openURL, smsLink, tgLink, waLink, reminderTemplates, fillTemplate } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Card, Money, Segmented, toast } from "../components/kit";
import { Sheet } from "../components/Sheet";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { BankSection } from "../sections/BankSection";
import { RemindersSection } from "../sections/RemindersSection";
import { NumberPicker } from "../components/ContactPicker";

type Seg = "ledger" | "lists" | "notes" | "bank" | "reminders" | "info";

export const ContactDetailOverlay: React.FC<{ params: Extract<Overlay, { type: "contact" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const open = useUI((s) => s.open);
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === params.recordID));
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const countryCode = useApp((s) => s.settings.countryCode);
  const logMessage = useApp((s) => s.logMessage);
  const [seg, setSeg] = useState<Seg>("ledger");
  const [numPicker, setNumPicker] = useState<{ action: "call" | "sms" | "whatsapp" } | null>(null);

  const ownerId = contact ? contactOwnerId(contact.recordID) : null;
  const balance = ownerId ? ownerFullBalance(entries, lists, ownerId) : 0;
  const pendingLists = ownerId ? openListsPending(lists, ownerId) : 0;
  const phone = contact?.phones[0]?.number;
  const email = contact?.emails[0]?.address;

  if (!contact) {
    return (
      <Sheet title={t("cd.notFound")} subtitle={t("cd.notFoundHint")} onBack={pop}>
        <Text style={{ color: C.sub, fontSize: F.body, textAlign: "center", paddingVertical: 30 }}>{t("cd.notFoundHint")}</Text>
      </Sheet>
    );
  }

  const runAction = (action: "call" | "sms" | "whatsapp") => {
    if (contact!.phones.length > 1) {
      setNumPicker({ action });
      return;
    }
    fireAction(action, contact!.phones[0]?.number);
  };

  const fireAction = (action: "call" | "sms" | "whatsapp", number?: string) => {
    if (!number) {
      toast(t("cd.noPhone"), "error");
      return;
    }
    if (action === "call") placeCall(number);
    if (action === "sms") openURL(smsLink(number));
    if (action === "whatsapp") {
      const text = balance > 0
        ? fillTemplate(reminderTemplates[0].body, {
            name: displayName(contact),
            amount: money(Math.abs(balance)),
            date: fd(Date.now()),
          })
        : balance < 0
          ? `Hi ${displayName(contact)}, I'd like to settle up ${money(Math.abs(balance))} that I owe you. How would you like to receive it?`
          : undefined;
      if (ownerId) logMessage({ ownerId, channel: "whatsapp", text: text ?? "" });
      openURL(waLink(number, text, countryCode));
    }
    setNumPicker(null);
  };

  const quickActions = [
    { icon: "phone", label: t("act.call"), run: () => runAction("call"), disabled: !phone },
    { icon: "message-text-outline", label: t("act.sms"), run: () => runAction("sms"), disabled: !phone },
    { icon: "whatsapp", label: t("act.whatsapp"), run: () => runAction("whatsapp"), disabled: !phone },
    { icon: "send-circle-outline", label: t("act.telegram"), run: () => phone && openURL(tgLink(`+${phone.replace(/\D/g, "")}`)), disabled: !phone },
    { icon: "email-outline", label: t("act.email"), run: () => email && openURL(`mailto:${email}`), disabled: !email },
    { icon: "book-outline", label: t("tab.ledger"), run: () => setSeg("ledger"), disabled: false },
  ];

  return (
    <Sheet
      title={displayName(contact)}
      subtitle={contact.company || undefined}
      onBack={pop}
      scroll={false}
    >
      {/* hero */}
      <View style={{ alignItems: "center", paddingTop: 8, paddingBottom: 14 }}>
        <Avatar contact={contact} size={86} />
        <Text style={{ fontSize: F.title, fontWeight: "800", color: C.ink, marginTop: 8, textAlign: "center" }}>
          {displayName(contact)}
        </Text>
        {!!(contact.company || contact.jobTitle) && (
          <Text style={{ fontSize: F.small, color: C.sub, marginTop: 2 }}>
            {[contact.company, contact.jobTitle].filter(Boolean).join(" · ")}
          </Text>
        )}
        <View style={{ alignItems: "center", marginTop: 8 }}>
          <Money value={balance} signed colorize size={22} weight="800" />
          <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 2 }}>
            {balance > 0.005 ? t("cd.theyOweYou") : balance < -0.005 ? t("cd.youOweThem") : t("cd.settledUp")}
          </Text>
          {pendingLists > 0.005 && (
            <Text style={{ fontSize: F.tiny, color: C.amber, marginTop: 2 }}>
              {t("lg.openLists")}: {money(pendingLists)}
            </Text>
          )}
        </View>
      </View>

      {/* quick actions */}
      <View style={{ flexDirection: "row", flexWrap: "wrap", justifyContent: "center", gap: 8, marginBottom: 14 }}>
        {quickActions.map((a) => (
          <Pressable
            key={a.label}
            disabled={a.disabled}
            onPress={a.run}
            android_ripple={{ color: "rgba(128,128,128,0.16)", borderless: true, radius: 40 }}
            style={({ pressed }) => [
              cdStyles.quick,
              { backgroundColor: pressed ? C.brandSoft : C.card, borderColor: C.line, opacity: a.disabled ? 0.4 : 1 },
            ]}
          >
            <Icon name={a.icon} size={21} color={a.disabled ? C.faint : C.brand} />
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700" }} numberOfLines={1}>
              {a.label}
            </Text>
          </Pressable>
        ))}
      </View>

      {/* segmented */}
      <View style={{ marginBottom: 12 }}>
        <Segmented
          value={seg}
          onChange={(k) => setSeg(k as Seg)}
          options={[
            { key: "ledger", label: t("tab.ledger") },
            { key: "lists", label: t("tab.lists") },
            { key: "notes", label: t("tab.notes") },
            { key: "bank", label: t("tab.bank") },
            { key: "reminders", label: t("tab.reminders") },
            { key: "info", label: t("tab.info") },
          ]}
        />
      </View>

      <ScrollView contentContainerStyle={{ paddingBottom: 30 }} keyboardShouldPersistTaps="handled">
        {seg === "ledger" && ownerId && <LedgerSection ownerId={ownerId} />}
        {seg === "lists" && ownerId && <ListsSection ownerId={ownerId} />}
        {seg === "notes" && ownerId && <NotesSection ownerId={ownerId} />}
        {seg === "bank" && ownerId && <BankSection ownerId={ownerId} />}
        {seg === "reminders" && ownerId && <RemindersSection ownerId={ownerId} />}
        {seg === "info" && <ContactInfo contact={contact} />}
      </ScrollView>

      {/* number picker */}
      {numPicker && (
        <NumberPicker
          recordID={contact.recordID}
          onPick={(number) => fireAction(numPicker.action, number)}
          onClose={() => setNumPicker(null)}
        />
      )}
    </Sheet>
  );
};

/* ---------------- info tab ---------------- */

const ContactInfo: React.FC<{ contact: DeviceContact }> = ({ contact }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);

  return (
    <View>
      {!!contact.birthday && <Card style={{ marginBottom: 10 }}><Text style={{ fontSize: F.small, color: C.sub }}>🎂 {t("cd.birthday")}: {contact.birthday}</Text></Card>}

      <InfoBlock title={t("cd.phones")}>
        {contact.phones.map((p) => (
          <View key={p.id} style={cdStyles.infoRow}>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }}>{prettyPhone(p.number, countryCode)}</Text>
              <Text style={{ fontSize: F.tiny, color: C.sub }}>{t(`cd.${p.label}`) !== `cd.${p.label}` ? t(`cd.${p.label}`) : p.label}</Text>
            </View>
            <Pressable onPress={() => placeCall(p.number)} hitSlop={6} style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}>
              <Icon name="phone" size={17} color={C.brand} />
            </Pressable>
          </View>
        ))}
        {contact.phones.length === 0 && <Muted text={t("cd.noPhone")} />}
      </InfoBlock>

      <InfoBlock title={t("cd.emails")}>
        {contact.emails.map((e) => (
          <View key={e.id} style={cdStyles.infoRow}>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }} numberOfLines={1}>{e.address}</Text>
              <Text style={{ fontSize: F.tiny, color: C.sub }}>{e.label}</Text>
            </View>
            <Pressable onPress={() => openURL(`mailto:${e.address}`)} hitSlop={6} style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}>
              <Icon name="email-outline" size={17} color={C.brand} />
            </Pressable>
          </View>
        ))}
        {contact.emails.length === 0 && <Muted text={t("cd.noEmail")} />}
      </InfoBlock>

      {!!contact.addresses.length && (
        <InfoBlock title={t("cd.addresses")}>
          {contact.addresses.map((a) => (
            <Text key={a.id} style={{ fontSize: F.small, color: C.ink, marginBottom: 4 }}>
              {a.formatted} ({a.label})
            </Text>
          ))}
        </InfoBlock>
      )}

      {!!contact.note && (
        <InfoBlock title={t("cd.notesSection")}>
          <Text style={{ fontSize: F.small, color: C.sub, lineHeight: 20 }}>{contact.note}</Text>
        </InfoBlock>
      )}
      <Btn
        small
        label={t("act.edit")}
        icon="pencil-outline"
        variant="soft"
        onPress={async () => {
          const ok = await openExistingContactEditor(contact.recordID);
          if (!ok) toast(t("act.edit") + " ✕", "error");
        }}
        style={{ alignSelf: "flex-start", marginTop: 4 }}
      />
    </View>
  );
};

const Muted: React.FC<{ text: string }> = ({ text }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return <Text style={{ fontSize: F.small, color: C.faint }}>{text}</Text>;
};

const InfoBlock: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Card style={{ marginBottom: 10 }}>
      <Text style={{ fontSize: F.small, fontWeight: "800", color: C.brand, marginBottom: 6 }}>{title}</Text>
      {children}
    </Card>
  );
};

const cdStyles = StyleSheet.create({
  quick: {
    alignItems: "center",
    justifyContent: "center",
    gap: 4,
    borderRadius: 16,
    borderWidth: 1,
    width: "30%",
    minWidth: 96,
    paddingVertical: 10,
    minHeight: 64,
  },
  infoRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 },
  miniBtn: { width: 34, height: 34, borderRadius: 17, alignItems: "center", justifyContent: "center" },
});
