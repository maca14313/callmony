/* Editors — NoteEdit · ReminderEdit · BankEdit overlays. */

import React, { useEffect, useMemo, useState } from "react";
import { Platform, Pressable, StyleSheet, Switch, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { dateStr, fd, ft, parseDueLocal } from "../lib/utils";
import type { ReminderKind, Repeat } from "../lib/types";
import { notificationsEnabled, requestNotificationPermission } from "../native/notify";
import { isBatteryExempt, requestBatteryExemption } from "../native/diag";
import { F, useThemeColors } from "../theme";
import { Btn, Chip, Field, SectionHeader, confirm, toast } from "../components/kit";
import { Sheet } from "../components/Sheet";
import { ContactPicker, useOwnerName } from "../components/ContactPicker";
import { NOTE_COLOR_OPTIONS } from "../sections/NotesSection";

/* ================= NoteEdit ================= */

export const NoteEditOverlay: React.FC<{ params: Extract<Overlay, { type: "noteEdit" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const notes = useApp((s) => s.notes);
  const saveNote = useApp((s) => s.saveNote);
  const deleteNote = useApp((s) => s.deleteNote);
  const existing = notes.find((n) => n.id === params.noteId);
  const ownerName = useOwnerName(params.ownerId);

  const [title, setTitle] = useState(existing?.title ?? "");
  const [body, setBody] = useState(existing?.body ?? "");
  const [tags, setTags] = useState((existing?.tags ?? []).join(", "));
  const [pinned, setPinned] = useState(existing?.pinned ?? false);
  const [color, setColor] = useState<string | undefined>(existing?.color);

  const save = () => {
    if (!title.trim() && !body.trim()) {
      toast(t("nt.titleRequired"), "error");
      return;
    }
    saveNote({
      id: existing?.id,
      ownerId: params.ownerId,
      title: title.trim(),
      body: body.trim(),
      tags: tags.split(",").map((x) => x.trim().replace(/^#/, "")).filter(Boolean),
      pinned,
      color,
    });
    toast(existing ? t("nt.noteUpdated") : t("nt.noteSaved"), "ok");
    pop();
  };

  return (
    <Sheet
      title={existing ? t("nt.editNote") : t("nt.newNote")}
      subtitle={ownerName}
      onBack={pop}
      footer={
        <View style={{ flexDirection: "row", gap: 10 }}>
          {existing && (
            <Btn
              label={t("act.delete")}
              icon="trash-can-outline"
              variant="danger"
              style={{ flex: 0.55 }}
              onPress={async () => {
                const ok = await confirm(t("nt.deleteTitle"), t("nt.deleteMsg"), t("act.delete"), true);
                if (ok) {
                  deleteNote(existing.id);
                  pop();
                }
              }}
            />
          )}
          <Btn label={t("act.save")} icon="content-save-outline" onPress={save} style={{ flex: 1 }} />
        </View>
      }
    >
      <Field label={t("nt.noteTitle")} value={title} onChangeText={setTitle} placeholder={t("nt.titleRequired")} autoFocus />
      <Field label={t("nt.body")} value={body} onChangeText={setBody} placeholder={t("nt.writeAnything")} multiline />
      <Field label={t("nt.noteTags")} value={tags} onChangeText={setTags} placeholder={t("nt.tagsPlaceholder")} />

      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 14 }}>
        <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{t("nt.pinnedHint")}</Text>
        <Switch value={pinned} onValueChange={setPinned} trackColor={{ true: C.brand, false: C.line }} thumbColor="#fff" />
      </View>

      <SectionHeader title={t("nt.color")} />
      <View style={{ flexDirection: "row", gap: 10, marginBottom: 14 }}>
        <Pressable onPress={() => setColor(undefined)} style={[edStyles.colorDot, { backgroundColor: "transparent", borderWidth: 1, borderColor: C.line }]}>
          <Icon name="close" size={15} color={C.sub} />
        </Pressable>
        {NOTE_COLOR_OPTIONS.map((c) => (
          <Pressable
            key={c}
            onPress={() => setColor(c)}
            style={[edStyles.colorDot, { backgroundColor: c, borderWidth: color === c ? 2.5 : 0, borderColor: C.brand }]}
          />
        ))}
      </View>
      <Text style={{ fontSize: F.tiny, color: C.faint }}>{t("nt.tagsExample")}</Text>
    </Sheet>
  );
};

/* ================= ReminderEdit ================= */

export const ReminderEditOverlay: React.FC<{ params: Extract<Overlay, { type: "reminderEdit" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const reminders = useApp((s) => s.reminders);
  const saveReminder = useApp((s) => s.saveReminder);
  const deleteReminder = useApp((s) => s.deleteReminder);
  const existing = reminders.find((r) => r.id === params.reminderId) ?? (params.prefill ? undefined : undefined);
  const prefill = params.prefill;
  const ownerName = useOwnerName(params.ownerId);

  const [title, setTitle] = useState(existing?.title ?? prefill?.title ?? "");
  const [notes, setNotes] = useState(existing?.notes ?? prefill?.notes ?? "");
  const [kind, setKind] = useState<ReminderKind>(existing?.kind ?? prefill?.kind ?? "custom");
  const [date, setDate] = useState(dateStr(existing?.dueAt ?? prefill?.dueAt ?? Date.now() + 86400000));
  const [time, setTime] = useState(ft(existing?.dueAt ?? prefill?.dueAt ?? Date.now() + 86400000));
  const [repeat, setRepeat] = useState<Repeat>(existing?.repeat ?? "none");
  const [lead, setLead] = useState<number[]>(existing?.leadMinutes ?? []);
  const [pickOwner, setPickOwner] = useState(false);
  const [ownerId, setOwnerId] = useState<string>(existing?.ownerId ?? params.ownerId);
  /* v1.6.2 — live notification status: tells the user whether this
     reminder will also RING while the app is closed (not just in-app). */
  const [notifOn, setNotifOn] = useState<boolean | null>(null);
  useEffect(() => {
    notificationsEnabled().then(setNotifOn).catch(() => setNotifOn(null));
  }, []);

  const askNotifications = async () => {
    const ok = await requestNotificationPermission();
    setNotifOn(ok);
    useApp.getState().updateSettings({ notifAsked: true });
    if (ok) toast(t("rm.notifGranted"), "ok");
  };

  const duePreview = useMemo(() => {
    /* v1.6.1 — forgiving parser: accepts 9:00 AM / 14:30 / 9.30 / 2 pm and
       09-01-2026 / Jan 9 / today / tomorrow — the old `${date}T${time}`
       parser NaN'd on everything but the exact ISO shape, which is why the
       user kept hitting "Pick a valid date and time". */
    return parseDueLocal(date, time);
  }, [date, time]);

  const save = async () => {
    let due = duePreview;
    if (!due) {
      toast(t("rm.validDateTime"), "error");
      return;
    }
    if (due.getTime() < Date.now() && repeat === "none" && !existing) {
      const ok = await confirm(t("rm.pastTitle"), t("rm.pastMsg"), t("rm.saveAnyway"));
      if (!ok) return;
    }
    /* v1.6.2 — ask for notification permission AT SAVE TIME: this is the
       moment the user actually needs reminders to ring in the background,
       so the dialog can never be missed (it used to live only on the
       Reminders tab). No-op when already granted/denied. */
    if (notifOn !== true) {
      requestNotificationPermission()
        .then((ok) => {
          setNotifOn(ok);
          useApp.getState().updateSettings({ notifAsked: true });
        })
        .catch(() => undefined);
    }
    /* v1.6.7 — ONE-TIME battery-exemption ask (ported from v1.6.4): OEM
       battery savers (Tecno/Infinix/Xiaomi/Samsung) force-kill background
       apps and drop their scheduled alarms — the #1 field cause of
       "reminders don't work when the app is closed". Shown once per
       install, right when the user starts relying on ringing reminders. */
    if (Platform.OS === "android" && !useApp.getState().settings.batteryAsked) {
      isBatteryExempt()
        .then(async (exempt) => {
          if (exempt === false) await requestBatteryExemption();
        })
        .catch(() => undefined)
        .finally(() => useApp.getState().updateSettings({ batteryAsked: true }));
    }
    saveReminder({
      id: existing?.id,
      ownerId: ownerId as never,
      title: title.trim(),
      notes: notes.trim() || undefined,
      kind,
      dueAt: due.getTime(),
      repeat,
      leadMinutes: lead.length ? lead : undefined,
    });
    toast(existing ? t("rm.reminderUpdated") : t("rm.reminderSaved"), "ok");
    pop();
  };

  return (
    <Sheet
      title={existing ? t("rm.editReminder") : t("rm.addReminder")}
      subtitle={ownerName}
      onBack={pop}
      footer={
        <View style={{ flexDirection: "row", gap: 10 }}>
          {existing && (
            <Btn
              label={t("act.delete")}
              icon="trash-can-outline"
              variant="danger"
              style={{ flex: 0.55 }}
              onPress={async () => {
                const ok = await confirm(t("rm.deleteReminder"), t("rm.deleteMsg"), t("act.delete"), true);
                if (ok) {
                  deleteReminder(existing.id);
                  pop();
                }
              }}
            />
          )}
          <Btn label={t("act.save")} icon="bell-check-outline" onPress={save} style={{ flex: 1 }} />
        </View>
      }
    >
      <Field label={t("rm.reminderTitle")} value={title} onChangeText={setTitle} placeholder={t("rm.whatShouldRemind")} autoFocus />
      <Field label={t("rm.reminderNotes")} value={notes} onChangeText={setNotes} placeholder={t("lg.optionalNote")} multiline />

      <SectionHeader title={t("rm.kindLabel")} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        {(["payment", "todo", "birthday", "followup", "custom"] as ReminderKind[]).map((k) => (
          <Chip
            key={k}
            label={t(`rm.kind${k.charAt(0).toUpperCase()}${k.slice(1)}`)}
            active={kind === k}
            onPress={() => setKind(k)}
          />
        ))}
      </View>

      <SectionHeader title={t("rm.pickDateTime")} />
      <View style={{ flexDirection: "row", gap: 8 }}>
        <View style={{ flex: 1.3 }}>
          <Field label={t("rm.dueDate")} value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" />
        </View>
        <View style={{ flex: 1 }}>
          <Field label={t("rm.dueTime")} value={time} onChangeText={setTime} placeholder="9:00 AM" />
        </View>
      </View>
      {/* quick date chips — one tap instead of typing */}
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 8 }}>
        {[
          { label: t("rm.today"), at: Date.now() },
          { label: t("rm.tomorrow"), at: Date.now() + 86400000 },
          { label: t("rm.nextWeek"), at: Date.now() + 7 * 86400000 },
        ].map((q) => (
          <Chip
            key={q.label}
            compact
            label={q.label}
            icon="calendar-plus"
            onPress={() => setDate(dateStr(q.at))}
          />
        ))}
        {["9:00 AM", "1:00 PM", "6:00 PM", "8:00 PM"].map((tt) => (
          <Chip key={tt} compact label={tt} icon="clock-outline" onPress={() => setTime(tt)} />
        ))}
      </View>
      <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 10 }}>
        {t("rm.dateFormatsHint")}
      </Text>
      {duePreview && (
        <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 10 }}>
          {t("rm.due")}: {fd(duePreview)} · {ft(duePreview.getTime())}
        </Text>
      )}

      {/* v1.6.2 — notification status: green when the reminder will ring in
          the background, amber one-tap fixer when it will not. */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 12 }}>
        <Icon
          name={notifOn === true ? "bell-check-outline" : "bell-off-outline"}
          size={17}
          color={notifOn === true ? C.green : C.amber}
        />
        <Text style={{ flex: 1, fontSize: F.tiny, color: C.sub }}>
          {notifOn === true ? t("rm.notifOnHint") : notifOn === false ? t("rm.notifOffHint") : ""}
        </Text>
        {notifOn === false && (
          <Btn small label={t("rm.turnOn")} variant="soft" onPress={askNotifications} />
        )}
      </View>

      <SectionHeader title={t("rm.repeats")} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 8 }}>
        {(["none", "daily", "weekly", "monthly", "yearly"] as Repeat[]).map((r) => (
          <Chip
            key={r}
            label={r === "none" ? t("rm.repeatOnce") : t(`rm.repeat${r.charAt(0).toUpperCase()}${r.slice(1)}`)}
            active={repeat === r}
            onPress={() => setRepeat(r)}
          />
        ))}
      </View>
      <View style={{ marginBottom: 12 }}>
        <Chip
          label={t("rm.everyMonthFirst")}
          icon="calendar-month-outline"
          onPress={() => {
            setRepeat("monthly");
            setDate(`${date.slice(0, 4)}-${date.slice(5, 7)}-01`);
          }}
        />
      </View>

      <SectionHeader title={t("rm.alertEarly")} subtitle={t("rm.leadHint")} />
      <View style={{ flexDirection: "row", gap: 6, marginBottom: 12 }}>
        <Chip
          label={t("rm.lead1d")}
          icon="weather-sunset-up"
          active={lead.includes(1440)}
          onPress={() => setLead(lead.includes(1440) ? lead.filter((x) => x !== 1440) : [...lead, 1440])}
        />
        <Chip
          label={t("rm.lead1h")}
          icon="clock-fast"
          active={lead.includes(60)}
          onPress={() => setLead(lead.includes(60) ? lead.filter((x) => x !== 60) : [...lead, 60])}
        />
      </View>

      <SectionHeader title={t("rm.ownerLabel")} />
      <Pressable onPress={() => setPickOwner(true)} style={[edStyles.ownerRow, { borderColor: C.line, backgroundColor: C.card }]}>
        <Icon name="account-circle-outline" size={22} color={C.brand} />
        <Text style={{ flex: 1, fontSize: F.body, color: C.ink, fontWeight: "600" }}>{ownerName}</Text>
        <Text style={{ fontSize: F.tiny, color: C.faint }}>{t("rm.moveHint")}</Text>
      </Pressable>

      {pickOwner && (
        <ContactPicker
          title={t("rm.moveReminder")}
          onClose={() => setPickOwner(false)}
          onPick={(id) => {
            setOwnerId(id);
            setPickOwner(false);
          }}
        />
      )}
    </Sheet>
  );
};

/* ================= BankEdit ================= */

export const BankEditOverlay: React.FC<{ params: Extract<Overlay, { type: "bankEdit" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const banks = useApp((s) => s.banks);
  const saveBank = useApp((s) => s.saveBank);
  const existing = banks.find((b) => b.id === params.bankId);
  const ownerName = useOwnerName(params.ownerId);

  const [bankName, setBankName] = useState(existing?.bankName ?? "");
  const [accountNumber, setAccountNumber] = useState(existing?.accountNumber ?? "");
  const [holderName, setHolderName] = useState(existing?.holderName ?? "");
  const [balance, setBalance] = useState(existing?.balance != null ? String(existing.balance) : "");
  const [pickHolder, setPickHolder] = useState(false);
  const [ownerId, setOwnerId] = useState<string>(params.ownerId);

  const save = () => {
    if (!bankName.trim()) {
      toast(t("bk.bankNameRequired"), "error");
      return;
    }
    if (!accountNumber.trim()) {
      toast(t("bk.accountNumberRequired"), "error");
      return;
    }
    saveBank({
      id: existing?.id,
      ownerId: ownerId as never,
      label: bankName.trim(),
      bankName: bankName.trim(),
      accountNumber: accountNumber.trim(),
      holderName: holderName.trim() || undefined,
      balance: balance ? Number(balance) : undefined,
    });
    toast(existing ? t("bk.accountUpdated") : t("bk.accountSaved"), "ok");
    pop();
  };

  return (
    <Sheet
      title={existing ? t("bk.editAccount") : t("bk.newAccount")}
      subtitle={ownerName}
      onBack={pop}
      footer={<Btn label={t("act.save")} icon="content-save-outline" onPress={save} />}
    >
      <Field label={t("bk.bankName")} value={bankName} onChangeText={setBankName} placeholder={t("bk.bankPlaceholder")} autoFocus />
      <Field label={t("bk.accountNumber")} value={accountNumber} onChangeText={setAccountNumber} placeholder="•••• •••• •••• 1234" />
      <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 12 }}>{t("bk.maskedHint")}</Text>
      <Field label={t("bk.holderName")} value={holderName} onChangeText={setHolderName} placeholder={t("bk.holderPlaceholder")} />
      <Field label={t("lg.balance")} value={balance} onChangeText={setBalance} keyboardType="decimal-pad" placeholder="0" />
      <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 12 }}>{t("bk.holderOptional")}</Text>

      {ownerId === "general" && (
        <>
          <Btn small label={t("bk.chooseFromContacts")} icon="account-search-outline" variant="soft" onPress={() => setPickHolder(true)} style={{ alignSelf: "flex-start", marginBottom: 10 }} />
          {pickHolder && (
            <ContactPicker
              includeGeneral={false}
              title={t("bk.chooseFromContacts")}
              onClose={() => setPickHolder(false)}
              onPick={(id, label) => {
                setOwnerId(id);
                setHolderName(label);
                setPickHolder(false);
              }}
            />
          )}
        </>
      )}
      {ownerId !== "general" && (
        <View style={[edStyles.ownerRow, { borderColor: C.line, backgroundColor: C.card }]}>
          <Icon name="account-circle-outline" size={22} color={C.brand} />
          <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{ownerName}</Text>
        </View>
      )}
    </Sheet>
  );
};

const edStyles = StyleSheet.create({
  colorDot: { width: 34, height: 34, borderRadius: 17, alignItems: "center", justifyContent: "center" },
  ownerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 10,
  },
});
