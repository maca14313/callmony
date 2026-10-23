/* UnknownContact — contact-detail experience for a number that is NOT in
   the address book (v1.6.3, user request: "even if the phone number isn't
   saved it should go to contact detail, so it will also be saved").
   Tapping an unsaved call card in Recents opens THIS instead of a dead-end
   action sheet:
     • hero: Save to contacts (OS create form, number prefilled) → the
       contact opens in the full ContactDetail the moment it exists
     • the same quick-action tiles a saved contact gets: call · SMS ·
       WhatsApp · copy
     • the call history for this exact number, straight from the system
       call log — tap a row to call back
   No data is invented: nothing is written anywhere until the user saves
   the contact in the OS form. */

import React, { useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { copyText, dayGroup, durationStr, ft, nationalDigits, prettyPhone, relTime } from "../lib/utils";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { openWithFeedback, smsLink, waLink } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Btn, Card, toast } from "../components/kit";
import { Sheet } from "../components/Sheet";

interface HistoryRow {
  key: string;
  direction: "incoming" | "outgoing" | "missed";
  at: number;
  duration: number;
  phone: string;
}

export const UnknownContactOverlay: React.FC<{ params: Extract<Overlay, { type: "unknownContact" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const open = useUI((s) => s.open);
  const countryCode = useApp((s) => s.settings.countryCode);
  const systemCalls = useApp((s) => s.systemCalls);
  const [saving, setSaving] = useState(false);

  const phone = params.phone;
  const nat = nationalDigits(phone, countryCode);

  /* every system call to/from this exact number — matched on NATIONAL
     digits (country-code-agnostic, the same rule as search v1.6.2) */
  const history: HistoryRow[] = useMemo(
    () =>
      systemCalls
        .filter((c) => {
          const d = nationalDigits(c.phoneNumber ?? "", countryCode);
          return d.length >= 5 && d === nat;
        })
        .slice(0, 30)
        .map((c) => ({ key: c.id, direction: c.direction, at: c.timestamp, duration: c.duration, phone: c.phoneNumber ?? "" })),
    [systemCalls, nat, countryCode]
  );

  /* HERO — save the number as a real contact, then open its detail page */
  const saveToContacts = async () => {
    if (saving) return;
    setSaving(true);
    try {
      const saved = await openNewContactForm({ phone });
      if (saved) {
        toast(t("ce.contactSaved"), "ok");
        await useApp.getState().refreshDeviceContacts();
        /* the number now resolves to a recordID — open the REAL detail
           page in place of this sheet */
        const resolved = useApp.getState().deviceContacts.find((c) =>
          c.phones.some((p) => {
            const d = nationalDigits(p.number, countryCode);
            return d.length >= 5 && d === nat;
          })
        );
        pop();
        if (resolved) setTimeout(() => open({ type: "contact", recordID: resolved.recordID }), 120);
      }
      /* saved == null → the user backed out of the OS form: not an error,
         nothing to report */
    } finally {
      setSaving(false);
    }
  };

  const quickActions: { icon: string; label: string; run: () => void }[] = [
    { icon: "phone", label: t("act.call"), run: () => placeCall(phone) },
    { icon: "message-text-outline", label: t("act.sms"), run: () => openWithFeedback(smsLink(phone), t("act.couldntOpen")) },
    { icon: "whatsapp", label: t("act.whatsapp"), run: () => openWithFeedback(waLink(phone, undefined, countryCode), t("act.couldntOpen")) },
    {
      icon: "content-copy",
      label: t("cd.copyNumber"),
      run: () => copyText(phone).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error")),
    },
  ];

  const dirMeta = (d: HistoryRow["direction"]) =>
    d === "missed"
      ? { icon: "phone-missed", color: C.red, label: t("cd.missed") }
      : d === "incoming"
        ? { icon: "phone-incoming", color: C.green, label: t("cd.incoming") }
        : { icon: "phone-outgoing", color: C.brand, label: t("cd.outgoing") };

  return (
    <Sheet
      title={params.name || prettyPhone(phone, countryCode)}
      subtitle={t("cd.unsavedNumber")}
      onBack={pop}
      headerLeft={
        <View style={[uqStyles.avatar, { backgroundColor: C.brandSoft }]}>
          <Icon name="account-question-outline" size={22} color={C.brand} />
        </View>
      }
      onSubtitleLongPress={() =>
        copyText(phone).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error"))
      }
    >
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }} keyboardShouldPersistTaps="handled">
        {/* hero — SAVE TO CONTACTS: the reason this screen exists */}
        <Card style={{ marginBottom: 14, borderColor: C.brand }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 12 }}>
            <View style={[uqStyles.heroIcon, { backgroundColor: C.brand }]}>
              <Icon name="account-plus-outline" size={26} color={C.onBrand} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.body, fontWeight: "800", color: C.ink }}>{t("cd.saveToContacts")}</Text>
              <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 2 }}>{t("cd.saveToContactsHint")}</Text>
            </View>
          </View>
          <Btn
            label={t("cd.saveToContacts")}
            icon="account-plus-outline"
            onPress={saveToContacts}
            disabled={saving}
            style={{ marginTop: 12, minHeight: 52 }}
          />
        </Card>

        {/* quick actions — same tiles a saved contact gets */}
        <View style={uqStyles.grid}>
          {quickActions.map((a) => (
            <Pressable
              key={a.label}
              onPress={a.run}
              android_ripple={{ color: "rgba(128,128,128,0.14)", borderless: true, radius: 46 }}
              style={({ pressed }) => [uqStyles.tile, { backgroundColor: pressed ? C.brandSoft : C.card, borderColor: C.line }]}
              accessibilityRole="button"
              accessibilityLabel={a.label}
            >
              <Icon name={a.icon} size={22} color={C.brand} />
              <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700", textAlign: "center" }} numberOfLines={1}>
                {a.label}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* call history for this exact number */}
        {history.length > 0 && (
          <>
            <Text style={{ fontSize: F.small, fontWeight: "800", color: C.ink, marginTop: 6, marginBottom: 8 }}>
              {t("cd.callHistoryFor")}
            </Text>
            {history.map((h) => {
              const meta = dirMeta(h.direction);
              return (
                <Pressable
                  key={h.key}
                  onPress={() => placeCall(h.phone || phone)}
                  android_ripple={{ color: "rgba(128,128,128,0.1)" }}
                  style={({ pressed }) => [uqStyles.histRow, { backgroundColor: pressed ? C.chip : C.card, borderColor: C.line }]}
                >
                  <Icon name={meta.icon} size={17} color={meta.color} />
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ fontSize: F.small, fontWeight: "600", color: h.direction === "missed" ? C.red : C.ink }}>
                      {dayGroup(h.at)} · {ft(h.at)}
                    </Text>
                    <Text style={{ fontSize: F.tiny, color: C.sub }}>
                      {meta.label}
                      {h.duration > 0 ? ` · ${durationStr(h.duration)}` : ""}
                    </Text>
                  </View>
                  <Text style={{ fontSize: F.tiny, color: C.faint }}>{relTime(h.at)}</Text>
                  <Icon name="phone-outline" size={17} color={C.brand} />
                </Pressable>
              );
            })}
          </>
        )}

        <Text style={{ fontSize: F.tiny, color: C.faint, marginTop: 14, textAlign: "center" }}>
          {t("cd.unsavedHint")}
        </Text>
      </ScrollView>
    </Sheet>
  );
};

const uqStyles = StyleSheet.create({
  avatar: { width: 34, height: 34, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  heroIcon: { width: 46, height: 46, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 6 },
  tile: {
    flexBasis: "22%",
    flexGrow: 1,
    maxWidth: 92,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    paddingVertical: 12,
    minHeight: 74,
  },
  histRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 9,
    marginBottom: 6,
    minHeight: 54,
  },
});
