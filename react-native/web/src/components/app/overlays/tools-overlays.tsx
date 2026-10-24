"use client";

/* =========================================================================
   Task 5-c — tools overlays
   NoteEdit · ReminderEdit · BankEdit — full-screen Sheets opened from the
   overlay stack. File attachments were REMOVED by user request — notes and
   reminders link to other entities via LinkChips/AddLinkButton instead, and
   BankEdit is simplified to exactly two fields (bank name + account number;
   the owner is implied by where the account is created from).
   ========================================================================= */

import { useMemo, useState } from "react";
import {
  Calendar,
  Check,
  Clock3,
  Inbox,
  Pin,
  Repeat,
  Trash2,
  UserRound,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { useT } from "@/lib/i18n";
import { dateStr, displayName, ft, todayStr } from "@/lib/utils";
import type {
  ID,
  LinkRef,
  OwnerId,
  Reminder,
  ReminderKind,
  Repeat as RepeatKind,
} from "@/lib/types";
import { Sheet } from "../kit/Sheet";
import { useConfirm } from "../kit/Confirm";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";
import { AddLinkButton, LinkChips, useOpenLink } from "../kit/LinkPicker";
import { ContactPicker } from "../kit/ContactPicker";
import { Avatar, Button, Chip, Field, SectionLabel, cx, inputCls } from "../kit/primitives";

/* ------------------------------------------------------------------ */
/* shared bits                                                          */
/* ------------------------------------------------------------------ */

const p2 = (n: number) => String(n).padStart(2, "0");
const hhmm = (epoch: number) => {
  const d = new Date(epoch);
  return `${p2(d.getHours())}:${p2(d.getMinutes())}`;
};

/** "in Abel Tesfaye" / "in General" */
function useOwnerLabel(ownerId: OwnerId): string {
  const t = useT();
  const contact = useApp((s) =>
    ownerId === "general" ? undefined : s.contacts.find((c) => c.id === ownerId && !c.deletedAt)
  );
  return ownerId === "general"
    ? `${t("misc.in")} ${t("misc.general")}`
    : `${t("misc.in")} ${displayName(contact)}`;
}

/* ------------------------------------------------------------------ */
/* NoteEdit                                                             */
/* ------------------------------------------------------------------ */

const NOTE_COLORS = ["#bfe3d9", "#f2ddcd", "#dcdff5", "#f4d7e8", "#eee7cf"];

const parseTags = (s: string): string[] =>
  s
    .split(",")
    .map((t) => t.trim().replace(/^#/, ""))
    .filter(Boolean);

export function NoteEdit({
  noteId,
  ownerId,
  linkTo,
}: {
  noteId?: ID;
  ownerId: ID | "general";
  linkTo?: LinkRef;
}) {
  const ui = useUI();
  const t = useT();
  const confirm = useConfirm();
  const openLink = useOpenLink();
  const ownerLabel = useOwnerLabel(ownerId);
  const note = useApp((s) => (noteId ? s.notes.find((n) => n.id === noteId && !n.deletedAt) : undefined));
  const saveNote = useApp((s) => s.saveNote);
  const deleteNote = useApp((s) => s.deleteNote);

  const [title, setTitle] = useState(note?.title ?? "");
  const [body, setBody] = useState(note?.body ?? "");
  const [tagsStr, setTagsStr] = useState((note?.tags ?? []).join(", "));
  const [pinned, setPinned] = useState(note?.pinned ?? false);
  const [color, setColor] = useState<string | undefined>(note?.color);
  const [links, setLinks] = useState<LinkRef[]>(note?.links ?? (linkTo ? [linkTo] : []));

  const save = () => {
    if (!title.trim() && !body.trim()) {
      toastErr(t("nt.addTitleFirst"));
      return;
    }
    saveNote({
      id: noteId,
      ownerId,
      title: title.trim() || "Untitled note",
      body,
      tags: parseTags(tagsStr),
      pinned,
      color: color || "",
      links,
    });
    toastOk(noteId ? "Note updated" : "Note saved");
    ui.pop();
  };

  const remove = async () => {
    if (!noteId) return;
    const ok = await confirm({
      title: "Delete note?",
      message: `“${note?.title || "Untitled note"}” will be moved to Trash — you can undo right after.`,
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    deleteNote(noteId);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={noteId ? t("nt.editNote") : t("nt.newNote")}
      subtitle={ownerLabel}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={save}>
            {t("act.save")}
          </Button>
          {noteId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">{t("act.delete")}</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label={t("nt.noteTitle")}>
          <input
            className={inputCls}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t("nt.noteTitle")}
            autoFocus={!noteId}
            aria-label={t("nt.noteTitle")}
          />
        </Field>

        <Field label={t("nt.body")}>
          <textarea
            className={cx(inputCls, "min-h-[160px] resize-y leading-relaxed")}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder={t("nt.writeAnything")}
            aria-label={t("nt.noteBody")}
          />
        </Field>

        <Field label={t("nt.noteTags")} hint={t("nt.tagsPlaceholder")}>
          <input
            className={inputCls}
            value={tagsStr}
            onChange={(e) => setTagsStr(e.target.value)}
            placeholder="work, invoice"
            aria-label={t("nt.noteTags")}
          />
        </Field>

        {/* pin switch */}
        <div className="flex items-center justify-between rounded-2xl border border-line bg-surface px-4 py-3">
          <div className="flex items-center gap-2.5">
            <Pin size={15} className={pinned ? "fill-current text-amber" : "text-mute"} />
            <div>
              <p className="text-[13px] font-semibold text-ink">{t("nt.pinned")}</p>
              <p className="text-[10px] text-mute">{t("nt.pinnedHint")}</p>
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={pinned}
            aria-label={t("nt.pinned")}
            onClick={() => setPinned((v) => !v)}
            className={cx(
              "flex h-8 w-[54px] items-center rounded-full px-1 transition",
              pinned ? "bg-brand" : "bg-line2"
            )}
          >
            <span
              className={cx(
                "h-6 w-6 rounded-full bg-white shadow-key transition-transform",
                pinned && "translate-x-[22px]"
              )}
            />
          </button>
        </div>

        {/* color dots */}
        <Field label={t("nt.color")}>
          <div className="flex items-center gap-2.5">
            {NOTE_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                aria-label={`${t("nt.color")} ${c}`}
                aria-pressed={color === c}
                onClick={() => setColor(color === c ? undefined : c)}
                style={{ background: c }}
                className={cx(
                  "h-10 w-10 rounded-full border-2 transition active:scale-90",
                  color === c ? "border-ink shadow-key" : "border-line"
                )}
              />
            ))}
            {color && (
              <button
                type="button"
                onClick={() => setColor(undefined)}
                className="ml-1 h-10 px-3 text-[10px] font-bold uppercase tracking-wide text-mute hover:text-ink"
              >
                {t("act.none")}
              </button>
            )}
          </div>
        </Field>

        {/* entity links */}
        <div>
          <SectionLabel>{t("ee.links")}</SectionLabel>
          <div className="space-y-2">
            {links.length > 0 && <LinkChips links={links} onChange={setLinks} onOpen={openLink} />}
            <AddLinkButton links={links} onChange={setLinks} label={t("nt.linkTo")} />
          </div>
        </div>
      </div>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* ReminderEdit                                                         */
/* ------------------------------------------------------------------ */

const KINDS: { id: ReminderKind; labelKey: string }[] = [
  { id: "payment", labelKey: "rm.kindPayment" },
  { id: "todo", labelKey: "rm.kindTodo" },
  { id: "birthday", labelKey: "rm.kindBirthday" },
  { id: "followup", labelKey: "rm.kindFollowup" },
  { id: "custom", labelKey: "rm.kindCustom" },
];

const REPEATS: { id: RepeatKind; label: string }[] = [
  { id: "none", label: "Once" },
  { id: "daily", label: "Daily" },
  { id: "weekly", label: "Weekly" },
  { id: "monthly", label: "Monthly" },
  { id: "yearly", label: "Yearly" },
];

const LEADS = [
  { m: 1440, label: "1 day before" },
  { m: 60, label: "1 hour before" },
];

export function ReminderEdit({
  reminderId,
  ownerId,
  prefill,
}: {
  reminderId?: ID;
  ownerId: ID | "general";
  prefill?: Partial<Reminder>;
}) {
  const ui = useUI();
  const t = useT();
  const confirm = useConfirm();
  const openLink = useOpenLink();
  const reminder = useApp((s) =>
    reminderId ? s.reminders.find((r) => r.id === reminderId && !r.deletedAt) : undefined
  );
  const saveReminder = useApp((s) => s.saveReminder);
  const deleteReminder = useApp((s) => s.deleteReminder);
  const moveEntity = useApp((s) => s.moveEntity);

  const initialDue = useMemo(() => {
    if (reminder?.dueAt) return reminder.dueAt;
    if (prefill?.dueAt) return prefill.dueAt;
    const t = new Date();
    t.setDate(t.getDate() + 1);
    t.setHours(9, 0, 0, 0);
    return t.getTime();
  }, []);

  const [title, setTitle] = useState(reminder?.title ?? prefill?.title ?? "");
  const [notes, setNotes] = useState(reminder?.notes ?? "");
  const [kind, setKind] = useState<ReminderKind>(reminder?.kind ?? prefill?.kind ?? "custom");
  const [date, setDate] = useState(dateStr(initialDue));
  const [time, setTime] = useState(hhmm(initialDue));
  const [repeat, setRepeat] = useState<RepeatKind>(reminder?.repeat ?? "none");
  const [lead, setLead] = useState<number[]>(reminder?.leadMinutes ?? []);
  const [links, setLinks] = useState<LinkRef[]>(reminder?.links ?? prefill?.links ?? []);
  const [owner, setOwner] = useState<OwnerId>(reminder?.ownerId ?? ownerId);
  const [pickerOpen, setPickerOpen] = useState(false);

  const ownerContact = useApp((s) =>
    owner === "general" ? undefined : s.contacts.find((c) => c.id === owner && !c.deletedAt)
  );
  const ownerName = owner === "general" ? t("misc.general") : displayName(ownerContact);

  const dueAt = useMemo(() => {
    const d = new Date(`${date || todayStr()}T${time || "09:00"}`);
    const v = d.getTime();
    return isNaN(v) ? NaN : v;
  }, [date, time]);

  const save = async () => {
    const trimmed = title.trim();
    if (!trimmed) {
      toastErr(t("nt.titleRequired"));
      return;
    }
    if (isNaN(dueAt)) {
      toastErr(t("rm.validDateTime"));
      return;
    }
    if (repeat === "none" && dueAt < Date.now()) {
      const ok = await confirm({
        title: "This time is already past",
        message: "Save anyway? It will appear in the Overdue group until you complete it.",
        confirmLabel: "Save anyway",
      });
      if (!ok) return;
    }
    saveReminder({
      id: reminderId,
      ownerId: owner,
      title: trimmed,
      notes,
      kind,
      dueAt,
      repeat,
      leadMinutes: lead,
      links,
    });
    toastOk(reminderId ? "Reminder updated" : "Reminder saved");
    ui.pop();
  };

  const remove = async () => {
    if (!reminderId) return;
    const ok = await confirm({
      title: t("rm.deleteReminder"),
      message: `“${reminder?.title || ""}” will be moved to Trash — you can undo right after.`,
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    deleteReminder(reminderId);
    toastUndoLast();
    ui.pop();
  };

  const toggleLead = (m: number) =>
    setLead((prev) => (prev.includes(m) ? prev.filter((x) => x !== m) : [...prev, m].sort((a, b) => a - b)));

  /** "Every month on the 1st" — monthly repeat, day pinned to the 1st, 9:00. */
  const presetMonthlyFirst = () => {
    const now = new Date();
    const d = new Date(now.getFullYear(), now.getMonth() + 1, 1, 9, 0, 0, 0);
    setRepeat("monthly");
    setDate(dateStr(d));
    setTime("09:00");
    toastOk(t("rm.monthlyFirst"));
  };

  const duePreview = isNaN(dueAt)
    ? "Pick a date and time"
    : `${new Date(dueAt).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })} · ${ft(dueAt)}${
        repeat !== "none" ? ` · repeats ${repeat}` : ""
      }`;

  return (
    <Sheet
      title={reminderId ? t("rm.editReminder") : "New reminder"}
      subtitle={duePreview}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={() => void save()}>
            <Check size={15} /> {t("act.save")}
          </Button>
          {reminderId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">{t("act.delete")}</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label={t("rm.reminderTitle")}>
          <input
            className={inputCls}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={t("rm.whatShouldRemind")}
            autoFocus={!reminderId}
            aria-label={t("rm.reminderTitle")}
          />
        </Field>

        <Field label={t("rm.reminderNotes")}>
          <textarea
            className={cx(inputCls, "min-h-[90px] resize-y")}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={t("nt.optionalDetails")}
            aria-label={t("rm.reminderNotes")}
          />
        </Field>

        {/* kind */}
        <div>
          <SectionLabel>Kind</SectionLabel>
          <div className="app-scroll flex gap-1.5 overflow-x-auto pb-1">
            {KINDS.map((k) => (
              <Chip key={k.id} active={kind === k.id} onClick={() => setKind(k.id)}>
                {t(k.labelKey)}
              </Chip>
            ))}
          </div>
        </div>

        {/* due date + time */}
        <div className="grid grid-cols-[1.5fr_1fr] gap-3">
          <Field label={t("rm.dueDate")}>
            <div className="relative">
              <input
                type="date"
                className={cx(inputCls, "pr-10")}
                value={date}
                onChange={(e) => setDate(e.target.value)}
                aria-label={t("rm.dueDate")}
              />
              <Calendar size={15} className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-mute" />
            </div>
          </Field>
          <Field label={t("rm.dueTime")}>
            <div className="relative">
              <input
                type="time"
                className={cx(inputCls, "pr-10")}
                value={time}
                onChange={(e) => setTime(e.target.value)}
                aria-label={t("rm.dueTime")}
              />
              <Clock3 size={15} className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-mute" />
            </div>
          </Field>
        </div>

        {/* repeat */}
        <div>
          <SectionLabel>{t("rm.repeat")}</SectionLabel>
          <div className="app-scroll flex gap-1.5 overflow-x-auto pb-1">
            {REPEATS.map((r) => (
              <Chip key={r.id} active={repeat === r.id} onClick={() => setRepeat(r.id)}>
                {repeat === r.id && <Repeat size={10} className="mr-1 inline" />}
                {r.label}
              </Chip>
            ))}
          </div>
          <div className="mt-2 flex gap-1.5">
            <button
              type="button"
              onClick={presetMonthlyFirst}
              className="flex h-11 items-center gap-1.5 rounded-full border border-brand/30 bg-brandsoft px-3.5 text-[11px] font-semibold text-brand transition active:scale-95"
            >
              <Repeat size={11} /> Every month on the 1st
            </button>
          </div>
        </div>

        {/* lead times */}
        <div>
          <SectionLabel>{t("rm.alertEarly")}</SectionLabel>
          <div className="flex gap-1.5">
            {LEADS.map((l) => (
              <Chip key={l.m} active={lead.includes(l.m)} onClick={() => toggleLead(l.m)}>
                {l.label}
              </Chip>
            ))}
          </div>
          <p className="mt-1.5 px-1 text-[10px] leading-4 text-mute">
            Lead alerts fire before the due time, while callmony is open.
          </p>
        </div>

        {/* owner */}
        <div>
          <SectionLabel>{t("rm.ownerLabel")}</SectionLabel>
          <div className="flex items-center justify-between rounded-2xl border border-line bg-surface px-3.5 py-2.5">
            <div className="flex min-w-0 items-center gap-2.5">
              {owner === "general" ? (
                <span className="flex h-10 w-10 items-center justify-center rounded-[14px] bg-surface2 text-mute">
                  <Inbox size={16} />
                </span>
              ) : (
                <Avatar contact={ownerContact} size="sm" />
              )}
              <div className="min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-[1.4px] text-mute">{t("nt.savedUnder")}</p>
                <p className="truncate text-[13px] font-semibold text-ink">{ownerName}</p>
              </div>
            </div>
            <Button variant="soft" size="md" onClick={() => setPickerOpen(true)}>
              Change
            </Button>
          </div>
          <p className="mt-1.5 px-1 text-[10px] leading-4 text-mute">
            {reminderId ? "Moving re-files this reminder under another contact." : undefined}
          </p>
        </div>

        {/* entity links */}
        <div>
          <SectionLabel>{t("ee.links")}</SectionLabel>
          <div className="space-y-2">
            {links.length > 0 && <LinkChips links={links} onChange={setLinks} onOpen={openLink} />}
            <AddLinkButton links={links} onChange={setLinks} label={t("lg.attachTo")} />
          </div>
        </div>
      </div>

      <ContactPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        allowGeneral
        title={t("rm.moveReminder")}
        onPick={(id) => {
          setOwner(id);
          if (reminderId && id !== reminder?.ownerId) moveEntity("reminder", reminderId, id);
        }}
      />
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* BankEdit — bank name + account number + holder name.                */
/* General accounts offer "Choose from contacts" — picking a contact    */
/* fills the holder name AND moves the account under that person.      */
/* ------------------------------------------------------------------ */

export function BankEdit({ bankId, ownerId }: { bankId?: ID; ownerId: ID | "general" }) {
  const ui = useUI();
  const t = useT();
  const confirm = useConfirm();
  const bank = useApp((s) => (bankId ? s.banks.find((b) => b.id === bankId && !b.deletedAt) : undefined));
  const saveBank = useApp((s) => s.saveBank);
  const deleteBank = useApp((s) => s.deleteBank);

  const [bankName, setBankName] = useState(bank?.bankName || bank?.label || "");
  const [accountNumber, setAccountNumber] = useState(bank?.accountNumber ?? "");
  const [holderName, setHolderName] = useState(bank?.holderName ?? "");
  /* local owner — "Choose from contacts" can link the account to a person */
  const [owner, setOwner] = useState<OwnerId>(ownerId);
  const [pickerOpen, setPickerOpen] = useState(false);

  const ownerLabel = useOwnerLabel(owner);

  /* person behind a non-general owner — for the read-only holder row */
  const ownerContact = useApp((s) =>
    owner === "general" ? undefined : s.contacts.find((c) => c.id === owner && !c.deletedAt)
  );
  const ownerName = owner === "general" ? undefined : displayName(ownerContact);

  const save = () => {
    const name = bankName.trim();
    const num = accountNumber.trim();
    if (!name) {
      toastErr(t("bk.bankNameRequired"));
      return;
    }
    if (!num) {
      toastErr(t("bk.accountNumberRequired"));
      return;
    }
    saveBank({
      id: bankId,
      ownerId: owner,
      /* label stays synced to the bank name so card rendering keeps working */
      label: name,
      bankName: name,
      /* trimmed value: empty string clears (store convention), never undefined */
      accountNumber: num,
      holderName: holderName.trim() || "",
    });
    toastOk(bankId ? t("bk.accountUpdated") : t("bk.accountSaved"));
    ui.pop();
  };

  const remove = async () => {
    if (!bankId) return;
    const ok = await confirm({
      title: t("bk.deleteTitle"),
      message: t("bk.deleteMsg", { name: bank?.bankName || bank?.label || "" }),
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    deleteBank(bankId);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={bankId ? t("bk.editAccount") : t("bk.newAccount")}
      subtitle={ownerLabel}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={save}>
            <Check size={15} /> {t("act.save")}
          </Button>
          {bankId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">{t("act.delete")}</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label={t("bk.bankName")}>
          <input
            className={inputCls}
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            placeholder={t("bk.bankPlaceholder")}
            autoFocus={!bankId}
            aria-label={t("bk.bankName")}
          />
        </Field>

        <Field label={t("bk.accountNumber")} hint={t("bk.maskedHint")}>
          <input
            className={cx(inputCls, "tracking-[1.5px] tabular-nums")}
            value={accountNumber}
            onChange={(e) => setAccountNumber(e.target.value)}
            placeholder={t("bk.numberPlaceholder")}
            inputMode="numeric"
            aria-label={t("bk.accountNumber")}
          />
        </Field>

        <Field label={t("bk.holderName")} hint={t("bk.holderOptional")}>
          <input
            className={inputCls}
            value={holderName}
            onChange={(e) => setHolderName(e.target.value)}
            placeholder="Abebe K."
            aria-label={t("bk.holderName")}
          />
        </Field>

        {owner === "general" ? (
          /* General account → offer linking a contact as the holder */
          <div>
            <button
              type="button"
              onClick={() => setPickerOpen(true)}
              aria-label={t("bk.chooseFromContacts")}
              className="flex h-11 w-full items-center justify-center gap-1.5 rounded-2xl border border-dashed border-line2 bg-surface2/60 text-[12px] font-semibold text-brand transition active:scale-[0.98]"
            >
              <UserRound size={15} /> {t("bk.chooseFromContacts")}
            </button>
            <ContactPicker
              open={pickerOpen}
              onClose={() => setPickerOpen(false)}
              allowGeneral={false}
              title={t("bk.chooseFromContacts")}
              onPick={(contactId, contactName) => {
                /* fill the holder field AND move the account under the person */
                setOwner(contactId);
                if (contactName) setHolderName(contactName);
                setPickerOpen(false);
              }}
            />
          </div>
        ) : (
          /* person's account → read-only holder hint */
          <div className="flex items-center gap-2.5 rounded-2xl border border-line bg-surface px-3.5 py-2.5">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
              <UserRound size={15} />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[10px] font-bold uppercase tracking-[1.4px] text-mute">{t("bk.holder")}</p>
              <p className="truncate text-[13px] font-semibold text-ink">{ownerName ?? "—"}</p>
            </div>
            <span className="shrink-0 rounded-full bg-brandsoft px-2 py-1 text-[9px] font-bold uppercase tracking-wide text-brand">
              {t("nt.savedUnder")} · {ownerName ?? "—"}
            </span>
          </div>
        )}
      </div>
    </Sheet>
  );
}
