/* Misc overlays — Search · SpeedDial · Merge duplicates · About · AlarmDialog. */

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Alert,
  Animated,
  Easing,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import Contacts from "react-native-contacts";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import { displayName, ft, money, normalizePhone, prettyPhone, vibrate } from "../lib/utils";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Card, EmptyState, IconBtn, Pill, confirm, toast } from "../components/kit";
import { Sheet, BottomSheet } from "../components/Sheet";
import { ContactPicker } from "../components/ContactPicker";
import { placeCall } from "../native/calls";
import { findDuplicateContacts } from "../native/deviceContacts";
import type { DeviceContact, Reminder } from "../lib/types";

/* ================= Search ================= */

export const SearchOverlay: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const open = useUI((s) => s.open);
  const contacts = useApp((s) => s.deviceContacts);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const countryCode = useApp((s) => s.settings.countryCode);
  const [q, setQ] = useState("");

  const ql = q.trim().toLowerCase();
  const qDigits = ql.replace(/\D/g, "");
  const hitContacts = useMemo(
    () =>
      ql.length >= 2
        ? contacts
            .filter((c) => {
              if (displayName(c).toLowerCase().includes(ql)) return true;
              /* dialer-style phone search — raw digits (09…) AND normalized
                 (+251…) both match, same as the keypad */
              if (qDigits.length >= 2) {
                return c.phones.some((p) => {
                  const raw = p.number.replace(/\D/g, "");
                  const norm = normalizePhone(p.number, countryCode).replace(/\D/g, "");
                  return raw.includes(qDigits) || norm.includes(qDigits);
                });
              }
              return false;
            })
            .slice(0, 6)
        : [],
    [ql, qDigits, contacts, countryCode]
  );
  const hitEntries = useMemo(
    () =>
      ql.length >= 2
        ? entries
            .filter((e) => !e.deletedAt && ((e.note ?? "").toLowerCase().includes(ql) || (e.category ?? "").toLowerCase().includes(ql) || String(e.amount).includes(ql)))
            .slice(0, 5)
        : [],
    [ql, entries]
  );
  const hitLists = useMemo(
    () => (ql.length >= 2 ? lists.filter((l) => !l.deletedAt && (l.title.toLowerCase().includes(ql) || l.items.some((i) => i.name.toLowerCase().includes(ql)))).slice(0, 5) : []),
    [ql, lists]
  );
  const hitNotes = useMemo(
    () => (ql.length >= 2 ? notes.filter((n) => !n.deletedAt && (n.title.toLowerCase().includes(ql) || n.body.toLowerCase().includes(ql))).slice(0, 5) : []),
    [ql, notes]
  );
  const hitReminders = useMemo(
    () => (ql.length >= 2 ? reminders.filter((r) => !r.deletedAt && (r.title.toLowerCase().includes(ql) || (r.notes ?? "").toLowerCase().includes(ql))).slice(0, 5) : []),
    [ql, reminders]
  );
  const hitBanks = useMemo(
    () => (ql.length >= 2 ? banks.filter((b) => !b.deletedAt && ((b.bankName ?? "").toLowerCase().includes(ql) || (b.holderName ?? "").toLowerCase().includes(ql))).slice(0, 5) : []),
    [ql, banks]
  );
  const total = hitContacts.length + hitEntries.length + hitLists.length + hitNotes.length + hitReminders.length + hitBanks.length;

  return (
    <Sheet title={t("sr.search")} onBack={pop}>
      <TextInput
        value={q}
        onChangeText={setQ}
        autoFocus
        placeholder={t("sr.placeholder")}
        placeholderTextColor={C.faint}
        style={[searchStyles.input, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
      />
      {ql.length < 2 && <Text style={{ color: C.sub, fontSize: F.small, textAlign: "center", paddingVertical: 20 }}>{t("sr.keepTyping")}</Text>}
      {ql.length >= 2 && total === 0 && <EmptyState icon="magnify-close" title={t("sr.noResults")} hint={t("sr.tryHint")} />}

      {hitContacts.length > 0 && (
        <ResultGroup title={t("nav.contacts")}>
          {hitContacts.map((c) => (
            <HitRow key={c.recordID} icon="account-circle-outline" title={displayName(c)} sub={c.phones[0] ? prettyPhone(c.phones[0].number) : t("cd.noPhone")} onPress={() => open({ type: "contact", recordID: c.recordID })} />
          ))}
        </ResultGroup>
      )}
      {hitEntries.length > 0 && (
        <ResultGroup title={t("sr.ledgerEntry")}>
          {hitEntries.map((e) => (
            <HitRow key={e.id} icon="book-outline" title={`${e.kind || e.type} · ${money(e.amount)}`} sub={e.note || e.category || e.date} onPress={() => open({ type: "entryEdit", entryId: e.id, ownerId: e.ownerId })} />
          ))}
        </ResultGroup>
      )}
      {hitLists.length > 0 && (
        <ResultGroup title={t("tab.lists")}>
          {hitLists.map((l) => (
            <HitRow key={l.id} icon="clipboard-list-outline" title={l.title} sub={`${l.items.length} ${t("ls.items")}`} onPress={() => open({ type: "list", listId: l.id })} />
          ))}
        </ResultGroup>
      )}
      {hitNotes.length > 0 && (
        <ResultGroup title={t("tab.notes")}>
          {hitNotes.map((n) => (
            <HitRow key={n.id} icon="notebook-outline" title={n.title || t("nt.untitled")} sub={n.body.slice(0, 40)} onPress={() => open({ type: "noteEdit", noteId: n.id, ownerId: n.ownerId })} />
          ))}
        </ResultGroup>
      )}
      {hitReminders.length > 0 && (
        <ResultGroup title={t("tab.reminders")}>
          {hitReminders.map((r) => (
            <HitRow key={r.id} icon="bell-outline" title={r.title} sub={r.notes || ""} onPress={() => open({ type: "reminderEdit", reminderId: r.id, ownerId: r.ownerId })} />
          ))}
        </ResultGroup>
      )}
      {hitBanks.length > 0 && (
        <ResultGroup title={t("tab.accounts")}>
          {hitBanks.map((b) => (
            <HitRow key={b.id} icon="bank-outline" title={b.bankName || b.label} sub={b.holderName ?? ""} onPress={() => open({ type: "bankEdit", bankId: b.id, ownerId: b.ownerId })} />
          ))}
        </ResultGroup>
      )}
    </Sheet>
  );
};

const ResultGroup: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={{ fontSize: F.small, fontWeight: "800", color: C.brand, marginBottom: 6 }}>{title}</Text>
      {children}
    </View>
  );
};

const HitRow: React.FC<{ icon: string; title: string; sub?: string; onPress: () => void }> = ({ icon, title, sub, onPress }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Pressable android_ripple={{ color: "rgba(128,128,128,0.12)" }} onPress={onPress} style={({ pressed }) => [searchStyles.hit, { backgroundColor: pressed ? C.chip : C.card, borderColor: C.line }]}>
      <Icon name={icon} size={19} color={C.sub} />
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }} numberOfLines={1}>{title}</Text>
        {!!sub && <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>{sub}</Text>}
      </View>
      <Icon name="chevron-right" size={18} color={C.faint} />
    </Pressable>
  );
};

/* ================= SpeedDial ================= */

export const SpeedDialOverlay: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const contacts = useApp((s) => s.deviceContacts);
  const contactX = useApp((s) => s.contactX);
  const speedDialSettings = useApp((s) => s.settings.speedDial);
  const setSpeedDial = useApp((s) => s.setSpeedDial);
  const setContactSpeedDial = useApp((s) => s.setContactSpeedDial);
  const [assignKey, setAssignKey] = useState<string | null>(null);

  const assigned = (key: string): DeviceContact | undefined => {
    const recordID = contactX.find((x) => x.speedDial === key)?.recordID ?? speedDialSettings?.[key];
    return contacts.find((c) => c.recordID === recordID);
  };

  const tapKey = (key: string) => {
    const target = assigned(key);
    if (!target) {
      setAssignKey(key);
      return;
    }
    const phone = target.phones[0]?.number;
    Alert.alert(`[${key}] ${displayName(target)}`, undefined, [
      { text: t("spd.callNow"), onPress: () => phone && placeCall(phone) },
      { text: t("spd.replace"), onPress: () => setAssignKey(key) },
      {
        text: t("spd.removeFromKey"),
        style: "destructive",
        onPress: () => {
          const recordID = target.recordID;
          setSpeedDial(key, null);
          if (contactX.find((x) => x.recordID === recordID)?.speedDial === key) setContactSpeedDial(recordID, null);
          toast(t("kp.speedDialCleared"), "ok");
        },
      },
      { text: t("act.cancel"), style: "cancel" },
    ]);
  };

  return (
    <Sheet title={t("spd.speedDial")} subtitle={t("spd.oneLongPress")} onBack={pop}>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 12 }}>
        {["2", "3", "4", "5", "6", "7", "8", "9"].map((key) => {
          const target = assigned(key);
          return (
            <Pressable
              key={key}
              onPress={() => tapKey(key)}
              android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true, radius: 44 }}
              style={({ pressed }) => [spStyles.key, { borderColor: C.line, backgroundColor: pressed ? C.chip : C.card }]}
            >
              <View style={{ alignItems: "center", gap: 4 }}>
                {target ? (
                  <Avatar contact={target} size={46} />
                ) : (
                  <View style={[spStyles.emptyAvatar, { borderColor: C.line }]}>
                    <Icon name="plus" size={22} color={C.faint} />
                  </View>
                )}
                <Text style={{ fontSize: 15, fontWeight: "800", color: target ? C.ink : C.faint }}>{key}</Text>
                <Text style={{ fontSize: F.tiny, color: C.sub, maxWidth: 74 }} numberOfLines={1}>
                  {target ? displayName(target) : t("spd.assignKey")}
                </Text>
              </View>
            </Pressable>
          );
        })}
      </View>
      {assignKey && (
        <ContactPicker
          includeGeneral={false}
          title={t("spd.assignToKey", { key: assignKey })}
          onClose={() => setAssignKey(null)}
          onPick={(ownerId) => {
            const recordID = ownerId.replace(/^c:/, "");
            setSpeedDial(assignKey, recordID);
            setContactSpeedDial(recordID, assignKey);
            setAssignKey(null);
            toast(t("kp.speedDialSet"), "ok");
          }}
        />
      )}
    </Sheet>
  );
};

/* ================= Merge duplicates ================= */

export const MergeOverlay: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const contacts = useApp((s) => s.deviceContacts);
  const [pairs, setPairs] = useState(() => findDuplicateContacts(contacts));
  const [scanning, setScanning] = useState(false);

  const rescan = () => {
    setScanning(true);
    setTimeout(() => {
      setPairs(findDuplicateContacts(useApp.getState().deviceContacts));
      setScanning(false);
    }, 400);
  };

  const mergePair = async (primary: DeviceContact, duplicate: DeviceContact) => {
    const ok = await confirm(t("spd.mergeTitle"), t("spd.mergeMsg", { name: displayName(primary) }), t("spd.mergeBtn"), true);
    if (!ok) return;
    try {
      const all = await Contacts.getContactsMatchingString("");
      const rawPrimary = all.find((c) => c.recordID === primary.recordID);
      const rawDup = all.find((c) => c.recordID === duplicate.recordID);
      if (!rawPrimary || !rawDup) return;
      // union phone numbers
      const dupPhones = rawDup.phoneNumbers.filter(
        (p) => !rawPrimary.phoneNumbers.some((x) => x.number.replace(/\D/g, "") === p.number.replace(/\D/g, ""))
      );
      const updated = {
        ...rawPrimary,
        phoneNumbers: [...rawPrimary.phoneNumbers, ...dupPhones],
        emailAddresses: [
          ...rawPrimary.emailAddresses,
          ...rawDup.emailAddresses.filter(
            (e) => !rawPrimary.emailAddresses.some((x) => x.email === e.email)
          ),
        ],
      };
      await Contacts.updateContact(updated);
      await Contacts.deleteContact(rawDup);
      toast(t("spd.contactsMerged"), "ok");
      rescan();
    } catch {
      toast(t("bkp.restoreFailed"), "error");
    }
  };

  return (
    <Sheet title={t("spd.mergeDuplicates")} subtitle={t("spd.scanSimilar")} onBack={pop} headerRight={<IconBtn icon="refresh" size={34} onPress={rescan} />}>
      {scanning && <Text style={{ color: C.sub, fontSize: F.small, marginBottom: 10 }}>{t("st.checking")}</Text>}
      {pairs.length === 0 ? (
        <EmptyState icon="account-check-outline" title={t("spd.noDuplicates")} hint={t("spd.everyUnique")} />
      ) : (
        <View>
          <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 10 }}>
            {t("spd.pairsFound", { n: pairs.length })}
          </Text>
          {pairs.map((p, i) => (
            <Card key={`${p.primary.recordID}-${i}`} style={{ marginBottom: 10 }}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                <Avatar contact={p.primary} size={40} />
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                    {displayName(p.primary)}
                  </Text>
                  <Pill label={t("spd.keep")} tone="green" />
                </View>
                <Icon name="arrow-right" size={18} color={C.faint} />
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: F.small, fontWeight: "700", color: C.sub }} numberOfLines={1}>
                    {displayName(p.duplicate)}
                  </Text>
                  <Pill label={t("spd.duplicate")} tone="neutral" />
                </View>
                <Avatar contact={p.duplicate} size={40} />
              </View>
              <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
                <Btn small label={t("spd.mergeBtn")} icon="merge" variant="soft" onPress={() => mergePair(p.primary, p.duplicate)} style={{ flex: 1 }} />
                <Btn
                  small
                  label={t("spd.dismissPair")}
                  variant="secondary"
                  onPress={() => setPairs((arr) => arr.filter((x) => x !== p))}
                  style={{ flex: 1 }}
                />
              </View>
            </Card>
          ))}
        </View>
      )}
    </Sheet>
  );
};

/* ================= About ================= */

export const AboutOverlay: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const features = [
    { icon: "phone", label: t("about.contactsCalls"), text: t("about.contactsCallsText") },
    { icon: "wallet-outline", label: t("about.moneyLedger"), text: t("about.moneyLedgerText") },
    { icon: "calculator-variant-outline", label: t("about.calcLists"), text: t("about.calcListsText") },
    { icon: "notebook-outline", label: t("tab.notes"), text: t("about.notesText") },
    { icon: "bell-outline", label: t("tab.reminders"), text: t("about.remindersText") },
    { icon: "bank-outline", label: t("tab.accounts"), text: t("about.banksText") },
    { icon: "cloud-off-outline", label: t("about.offline"), text: t("about.offlineText") },
  ];
  return (
    <Sheet title={t("st.aboutApp")} subtitle={t("st.aboutSubtitle")} onBack={pop}>
      <View style={{ alignItems: "center", marginBottom: 16 }}>
        <View style={[abStyles.logo, { backgroundColor: C.brand }]}>
          <Icon name="phone-in-talk" size={34} color={C.onBrand} />
        </View>
        <Text style={{ fontSize: F.hero, fontWeight: "800", color: C.ink, marginTop: 8 }}>callmony</Text>
        <Text style={{ fontSize: F.small, color: C.sub }}>{t("hdr.tagline")}</Text>
        <View style={{ flexDirection: "row", gap: 8, marginTop: 8 }}>
          <Pill label={`${t("st.version")} 1.0.0`} tone="brand" />
          <Pill label={t("about.offline")} tone="green" icon="cloud-off-outline" />
        </View>
      </View>
      <View style={{ gap: 8 }}>
        {features.map((f) => (
          <Card key={f.label}>
            <View style={{ flexDirection: "row", gap: 12, alignItems: "flex-start" }}>
              <Icon name={f.icon} size={20} color={C.brand} />
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }}>{f.label}</Text>
                <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 2, lineHeight: 18 }}>{f.text}</Text>
              </View>
            </View>
          </Card>
        ))}
      </View>
      <Text style={{ fontSize: F.tiny, color: C.faint, textAlign: "center", marginTop: 16, lineHeight: 18 }}>
        {t("sto.privacyNote")}
      </Text>
      <Text style={{ fontSize: F.tiny, color: C.faint, textAlign: "center", marginTop: 6 }}>
        {t("st.craftedFooter")}
      </Text>
    </Sheet>
  );
};

/* ================= AlarmDialog ================= */

export const AlarmDialog: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const alarm = useUI((s) => s.alarmQueue[0]);
  const shiftAlarm = useUI((s) => s.shiftAlarm);
  const completeReminder = useApp((s) => s.completeReminder);
  const snoozeReminder = useApp((s) => s.snoozeReminder);
  const fireReminder = useApp((s) => s.fireReminder);
  const pulse = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    if (!alarm) return;
    vibrate([0, 400, 200, 400]);
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.15, duration: 550, easing: Easing.out(Easing.quad), useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 550, easing: Easing.in(Easing.quad), useNativeDriver: true }),
      ])
    );
    loop.start();
    return () => loop.stop();
  }, [alarm, pulse]);

  if (!alarm) return null;
  const r: Reminder = alarm;
  const kindLabel =
    r.kind === "payment" ? t("rm.kindPayment") : r.kind === "birthday" ? t("rm.kindBirthday") : r.kind === "todo" ? t("rm.kindTodo") : r.kind === "followup" ? t("rm.kindFollowup") : t("rm.kindCustom");

  const dismiss = () => {
    if (r.repeat !== "none") {
      // advance to next occurrence
      fireReminder(r.id);
    } else {
      completeReminder(r.id);
    }
    shiftAlarm();
  };

  return (
    <Modal transparent animationType="fade" visible onRequestClose={dismiss}>
      <View style={[alStyles.scrim, { backgroundColor: C.scrim }]}>
        <View style={[alStyles.card, { backgroundColor: C.card }]}>
          <Animated.View style={[alStyles.bell, { backgroundColor: C.brand, transform: [{ scale: pulse }] }]}>
            <Icon name={r.kind === "birthday" ? "cake-variant-outline" : "bell-ring-outline"} size={30} color={C.onBrand} />
          </Animated.View>
          <Text style={{ fontSize: F.tiny, color: C.brand, fontWeight: "800", marginTop: 12 }}>{kindLabel}</Text>
          <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink, textAlign: "center", marginTop: 2 }}>
            {r.title || kindLabel}
          </Text>
          {!!r.notes && (
            <Text style={{ fontSize: F.small, color: C.sub, textAlign: "center", marginTop: 4 }}>{r.notes}</Text>
          )}
          <Text style={{ fontSize: F.small, color: C.sub, marginTop: 8 }}>
            {dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue" ? t("rm.overdue") : t("rm.today")} · {ft(r.snoozedUntil ?? r.dueAt)}
            {r.repeat !== "none" ? ` · ${t(`rm.repeat${r.repeat.charAt(0).toUpperCase()}${r.repeat.slice(1)}`)}` : ""}
          </Text>
          <View style={{ gap: 8, marginTop: 16, width: "100%" }}>
            <Btn label={t("act.done")} icon="check" onPress={dismiss} />
            <View style={{ flexDirection: "row", gap: 8 }}>
              <Btn small label={t("rm.snooze10")} icon="alarm-clock-plus" variant="secondary" onPress={() => { snoozeReminder(r.id, Date.now() + 600000); shiftAlarm(); }} style={{ flex: 1 }} />
              <Btn small label={t("rm.snooze1h")} icon="clock-fast" variant="secondary" onPress={() => { snoozeReminder(r.id, Date.now() + 3600000); shiftAlarm(); }} style={{ flex: 1 }} />
            </View>
            <Pressable onPress={dismiss} hitSlop={8} style={{ alignItems: "center", paddingVertical: 6 }}>
              <Text style={{ fontSize: F.small, color: C.sub }}>{t("act.close")}</Text>
            </Pressable>
          </View>
        </View>
      </View>
    </Modal>
  );
};

/* ---------------- styles ---------------- */

const searchStyles = StyleSheet.create({
  input: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, height: 48, fontSize: F.body, marginBottom: 14 },
  hit: { flexDirection: "row", alignItems: "center", gap: 10, borderRadius: 14, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 9, marginBottom: 5, minHeight: 54 },
});

const spStyles = StyleSheet.create({
  key: { flexBasis: "23%", flexGrow: 1, maxWidth: 120, aspectRatio: 0.9, borderRadius: 18, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  emptyAvatar: { width: 46, height: 46, borderRadius: 23, borderWidth: 1.5, borderStyle: "dashed", alignItems: "center", justifyContent: "center" },
});

const abStyles = StyleSheet.create({
  logo: { width: 70, height: 70, borderRadius: 22, alignItems: "center", justifyContent: "center" },
});

const alStyles = StyleSheet.create({
  scrim: { flex: 1, justifyContent: "center", alignItems: "center", padding: 28 },
  card: { borderRadius: 24, padding: 22, width: "100%", maxWidth: 340, alignItems: "center" },
  bell: { width: 62, height: 62, borderRadius: 31, alignItems: "center", justifyContent: "center" },
});
