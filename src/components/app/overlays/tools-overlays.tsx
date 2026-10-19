"use client";

/* =========================================================================
   Task 2-c — tools overlays
   NoteEdit · ReminderEdit · BankEdit — full-screen Sheets opened from the
   overlay stack. Attachments for NEW records use the "save draft first"
   flow sanctioned by the spec (save → get id → attach).
   ========================================================================= */

import { useMemo, useState } from "react";
import {
  Calendar,
  Check,
  Clock3,
  Inbox,
  Paperclip,
  Pin,
  Repeat,
  Trash2,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { dateStr, displayName, ft, todayStr } from "@/lib/utils";
import type {
  AttachmentMeta,
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
import { AttachButton, AttachmentStrip } from "../kit/Attachments";
import { AddLinkButton, LinkChips, useOpenLink } from "../kit/LinkPicker";
import { ContactPicker } from "../kit/ContactPicker";
import { CalcButton } from "../kit/Calculator";
import { Avatar, Button, Chip, Field, SectionLabel, cx, inputCls } from "../kit/primitives";

/* ------------------------------------------------------------------ */
/* shared bits                                                          */
/* ------------------------------------------------------------------ */

const p2 = (n: number) => String(n).padStart(2, "0");
const hhmm = (epoch: number) => {
  const d = new Date(epoch);
  return `${p2(d.getHours())}:${p2(d.getMinutes())}`;
};

/** "In Abel Tesfaye" / "In General" */
function useOwnerLabel(ownerId: OwnerId): string {
  const contact = useApp((s) =>
    ownerId === "general" ? undefined : s.contacts.find((c) => c.id === ownerId && !c.deletedAt)
  );
  return ownerId === "general" ? "In General" : `In ${displayName(contact)}`;
}

/** Attach row: real AttachButton once a draft id exists; "save draft" flow for new records. */
function AttachArea({
  parentType,
  draftId,
  ids,
  onAttached,
  onEnsureDraft,
}: {
  parentType: "note" | "reminder" | "bank";
  draftId: ID | undefined;
  ids: ID[];
  onAttached: (meta: AttachmentMeta) => void;
  onEnsureDraft: () => boolean;
}) {
  return (
    <div>
      <SectionLabel>Files</SectionLabel>
      <div className="flex items-center gap-2.5">
        {draftId ? (
          <AttachButton parentType={parentType} parentId={draftId} onAttached={onAttached} compact />
        ) : (
          <Button variant="soft" size="md" onClick={() => onEnsureDraft()}>
            <Paperclip size={14} /> Save draft &amp; attach
          </Button>
        )}
        <p className="text-[10px] leading-4 text-mute">
          {draftId
            ? `${ids.length} file${ids.length === 1 ? "" : "s"} attached`
            : "Files attach once the draft is saved"}
        </p>
      </div>
      {draftId && ids.length > 0 && (
        <div className="mt-2.5">
          <AttachmentStrip ids={ids} />
        </div>
      )}
    </div>
  );
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
  const [attachments, setAttachments] = useState<ID[]>(note?.attachments ?? []);
  const [draftId, setDraftId] = useState<ID | undefined>(noteId);

  /** Save current form as a draft so files can attach to a real id. */
  const ensureDraft = (): boolean => {
    if (draftId) return true;
    if (!title.trim() && !body.trim()) {
      toastErr("Write a title or some text first");
      return false;
    }
    const n = saveNote({
      ownerId,
      title: title.trim() || "Untitled note",
      body,
      tags: parseTags(tagsStr),
      pinned,
      color: color || "",
      links,
      attachments: [],
    });
    setDraftId(n.id);
    toastOk("Draft saved — pick files to attach");
    return true;
  };

  const onAttached = (meta: AttachmentMeta) => {
    const next = [...attachments, meta.id];
    setAttachments(next);
    if (draftId) saveNote({ id: draftId, ownerId, attachments: next });
  };

  const save = () => {
    if (!title.trim() && !body.trim()) {
      toastErr("Add a title or some text first");
      return;
    }
    saveNote({
      id: draftId,
      ownerId,
      title: title.trim() || "Untitled note",
      body,
      tags: parseTags(tagsStr),
      pinned,
      color: color || "",
      links,
      attachments,
    });
    toastOk(noteId ? "Note updated" : "Note saved");
    ui.pop();
  };

  const remove = async () => {
    if (!noteId) return;
    const ok = await confirm({
      title: "Delete note?",
      message: `“${note?.title || "Untitled note"}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    deleteNote(noteId);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={noteId ? "Edit note" : "New note"}
      subtitle={ownerLabel}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={save}>
            Save note
          </Button>
          {noteId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">Delete note</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label="Title">
          <input
            className={inputCls}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Note title"
            autoFocus={!noteId}
            aria-label="Note title"
          />
        </Field>

        <Field label="Body">
          <textarea
            className={cx(inputCls, "min-h-[160px] resize-y leading-relaxed")}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="Write anything…"
            aria-label="Note body"
          />
        </Field>

        <Field label="Tags" hint="Comma separated — e.g. work, invoice, todo">
          <input
            className={inputCls}
            value={tagsStr}
            onChange={(e) => setTagsStr(e.target.value)}
            placeholder="work, invoice"
            aria-label="Note tags"
          />
        </Field>

        {/* pin switch */}
        <div className="flex items-center justify-between rounded-2xl border border-line bg-surface px-4 py-3">
          <div className="flex items-center gap-2.5">
            <Pin size={15} className={pinned ? "fill-current text-amber" : "text-mute"} />
            <div>
              <p className="text-[13px] font-semibold text-ink">Pinned</p>
              <p className="text-[10px] text-mute">Pinned notes stay on top of the section</p>
            </div>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={pinned}
            aria-label="Pin note"
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
        <Field label="Color">
          <div className="flex items-center gap-2.5">
            {NOTE_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                aria-label={`Note color ${c}`}
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
                None
              </button>
            )}
          </div>
        </Field>

        {/* links */}
        <div>
          <SectionLabel>Links</SectionLabel>
          <div className="space-y-2">
            {links.length > 0 && <LinkChips links={links} onChange={setLinks} onOpen={openLink} />}
            <AddLinkButton links={links} onChange={setLinks} label="Link to contact, entry, list…" />
          </div>
        </div>

        <AttachArea
          parentType="note"
          draftId={draftId}
          ids={attachments}
          onAttached={onAttached}
          onEnsureDraft={ensureDraft}
        />
      </div>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* ReminderEdit                                                         */
/* ------------------------------------------------------------------ */

const KINDS: { id: ReminderKind; label: string }[] = [
  { id: "payment", label: "Payment" },
  { id: "todo", label: "To-do" },
  { id: "birthday", label: "Birthday" },
  { id: "followup", label: "Follow-up" },
  { id: "custom", label: "Custom" },
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
  const [attachments, setAttachments] = useState<ID[]>(reminder?.attachments ?? []);
  const [draftId, setDraftId] = useState<ID | undefined>(reminderId);
  const [owner, setOwner] = useState<OwnerId>(reminder?.ownerId ?? ownerId);
  const [pickerOpen, setPickerOpen] = useState(false);

  const ownerContact = useApp((s) =>
    owner === "general" ? undefined : s.contacts.find((c) => c.id === owner && !c.deletedAt)
  );
  const ownerName = owner === "general" ? "General" : displayName(ownerContact);

  const dueAt = useMemo(() => {
    const d = new Date(`${date || todayStr()}T${time || "09:00"}`);
    const v = d.getTime();
    return isNaN(v) ? NaN : v;
  }, [date, time]);

  const ensureDraft = (): boolean => {
    if (draftId) return true;
    if (!title.trim()) {
      toastErr("Add a title first");
      return false;
    }
    const r = saveReminder({
      ownerId: owner,
      title: title.trim(),
      notes,
      kind,
      dueAt: isNaN(dueAt) ? Date.now() + 86400000 : dueAt,
      repeat,
      leadMinutes: lead,
      links,
      attachments: [],
    });
    setDraftId(r.id);
    toastOk("Draft saved — pick files to attach");
    return true;
  };

  const onAttached = (meta: AttachmentMeta) => {
    const next = [...attachments, meta.id];
    setAttachments(next);
    if (draftId) saveReminder({ id: draftId, ownerId: owner, title: title.trim() || "Untitled", attachments: next });
  };

  const save = async () => {
    const t = title.trim();
    if (!t) {
      toastErr("Title is required");
      return;
    }
    if (isNaN(dueAt)) {
      toastErr("Pick a valid date and time");
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
      id: draftId,
      ownerId: owner,
      title: t,
      notes,
      kind,
      dueAt,
      repeat,
      leadMinutes: lead,
      links,
      attachments,
    });
    toastOk(reminderId ? "Reminder updated" : "Reminder saved");
    ui.pop();
  };

  const remove = async () => {
    if (!reminderId) return;
    const ok = await confirm({
      title: "Delete reminder?",
      message: `“${reminder?.title || ""}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
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
    const t = new Date();
    const d = new Date(t.getFullYear(), t.getMonth() + 1, 1, 9, 0, 0, 0);
    setRepeat("monthly");
    setDate(dateStr(d));
    setTime("09:00");
    toastOk("Repeats every month on the 1st");
  };

  const duePreview = isNaN(dueAt)
    ? "Pick a date and time"
    : `${new Date(dueAt).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" })} · ${ft(dueAt)}${
        repeat !== "none" ? ` · repeats ${repeat}` : ""
      }`;

  return (
    <Sheet
      title={reminderId ? "Edit reminder" : "New reminder"}
      subtitle={duePreview}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={() => void save()}>
            <Check size={15} /> Save reminder
          </Button>
          {reminderId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">Delete reminder</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label="Title">
          <input
            className={inputCls}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="What should remind you?"
            autoFocus={!reminderId}
            aria-label="Reminder title"
          />
        </Field>

        <Field label="Notes">
          <textarea
            className={cx(inputCls, "min-h-[90px] resize-y")}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Optional details…"
            aria-label="Reminder notes"
          />
        </Field>

        {/* kind */}
        <div>
          <SectionLabel>Kind</SectionLabel>
          <div className="app-scroll flex gap-1.5 overflow-x-auto pb-1">
            {KINDS.map((k) => (
              <Chip key={k.id} active={kind === k.id} onClick={() => setKind(k.id)}>
                {k.label}
              </Chip>
            ))}
          </div>
        </div>

        {/* due date + time */}
        <div className="grid grid-cols-[1.5fr_1fr] gap-3">
          <Field label="Date">
            <div className="relative">
              <input
                type="date"
                className={cx(inputCls, "pr-10")}
                value={date}
                onChange={(e) => setDate(e.target.value)}
                aria-label="Due date"
              />
              <Calendar size={15} className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-mute" />
            </div>
          </Field>
          <Field label="Time">
            <div className="relative">
              <input
                type="time"
                className={cx(inputCls, "pr-10")}
                value={time}
                onChange={(e) => setTime(e.target.value)}
                aria-label="Due time"
              />
              <Clock3 size={15} className="pointer-events-none absolute right-3.5 top-1/2 -translate-y-1/2 text-mute" />
            </div>
          </Field>
        </div>

        {/* repeat */}
        <div>
          <SectionLabel>Repeat</SectionLabel>
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
          <SectionLabel>Alert me early</SectionLabel>
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
          <SectionLabel>Owner</SectionLabel>
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
                <p className="text-[10px] font-bold uppercase tracking-[1.4px] text-mute">Saved under</p>
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

        {/* links */}
        <div>
          <SectionLabel>Links</SectionLabel>
          <div className="space-y-2">
            {links.length > 0 && <LinkChips links={links} onChange={setLinks} onOpen={openLink} />}
            <AddLinkButton links={links} onChange={setLinks} label="Attach to credit, list, note…" />
          </div>
        </div>

        <AttachArea
          parentType="reminder"
          draftId={draftId}
          ids={attachments}
          onAttached={onAttached}
          onEnsureDraft={ensureDraft}
        />
      </div>

      <ContactPicker
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        allowGeneral
        title="Move reminder to…"
        onPick={(id) => {
          setOwner(id);
          if (reminderId && id !== reminder?.ownerId) moveEntity("reminder", reminderId, id);
        }}
      />
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* BankEdit                                                             */
/* ------------------------------------------------------------------ */

export function BankEdit({ bankId, ownerId }: { bankId?: ID; ownerId: ID | "general" }) {
  const ui = useUI();
  const confirm = useConfirm();
  const ownerLabel = useOwnerLabel(ownerId);
  const bank = useApp((s) => (bankId ? s.banks.find((b) => b.id === bankId && !b.deletedAt) : undefined));
  const saveBank = useApp((s) => s.saveBank);
  const deleteBank = useApp((s) => s.deleteBank);

  const [label, setLabel] = useState(bank?.label ?? "");
  const [bankName, setBankName] = useState(bank?.bankName ?? "");
  const [holderName, setHolderName] = useState(bank?.holderName ?? "");
  const [accountNumber, setAccountNumber] = useState(bank?.accountNumber ?? "");
  const [iban, setIban] = useState(bank?.iban ?? "");
  const [swift, setSwift] = useState(bank?.swift ?? "");
  const [upiId, setUpiId] = useState(bank?.upiId ?? "");
  const [branch, setBranch] = useState(bank?.branch ?? "");
  const [balanceStr, setBalanceStr] = useState(bank?.balance != null ? String(bank.balance) : "");
  const [notes, setNotes] = useState(bank?.notes ?? "");
  const [attachments, setAttachments] = useState<ID[]>(bank?.attachments ?? []);
  const [draftId, setDraftId] = useState<ID | undefined>(bankId);

  const ensureDraft = (): boolean => {
    if (draftId) return true;
    if (!label.trim()) {
      toastErr("Add a label first");
      return false;
    }
    const bal = balanceStr.trim() ? parseFloat(balanceStr) : undefined;
    const b = saveBank({
      ownerId,
      label: label.trim(),
      bankName,
      holderName,
      accountNumber,
      iban,
      swift,
      upiId,
      branch,
      notes,
      balance: bal != null && !isNaN(bal) ? bal : undefined,
      attachments: [],
    });
    setDraftId(b.id);
    toastOk("Draft saved — pick files to attach");
    return true;
  };

  const onAttached = (meta: AttachmentMeta) => {
    const next = [...attachments, meta.id];
    setAttachments(next);
    if (draftId) saveBank({ id: draftId, ownerId, label: label.trim() || "Account", attachments: next });
  };

  const save = () => {
    if (!label.trim()) {
      toastErr("Label is required");
      return;
    }
    const bal = balanceStr.trim();
    let balance: number | undefined;
    if (bal) {
      const n = parseFloat(bal);
      if (isNaN(n)) {
        toastErr("Balance must be a number");
        return;
      }
      balance = n;
    }
    saveBank({
      id: draftId,
      ownerId,
      label: label.trim(),
      bankName,
      holderName,
      accountNumber,
      iban,
      swift,
      upiId,
      branch,
      notes,
      balance,
      attachments,
    });
    toastOk(bankId ? "Account updated" : "Account saved");
    ui.pop();
  };

  const remove = async () => {
    if (!bankId) return;
    const ok = await confirm({
      title: "Delete account?",
      message: `“${bank?.label || ""}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    deleteBank(bankId);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={bankId ? "Edit account" : "New bank account"}
      subtitle={ownerLabel}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          <Button variant="brand" size="lg" className="flex-1" onClick={save}>
            Save account
          </Button>
          {bankId && (
            <Button variant="danger" size="lg" className="w-[52px] px-0" onClick={() => void remove()}>
              <Trash2 size={16} />
              <span className="sr-only">Delete account</span>
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4 p-4 pb-10">
        <Field label="Label" hint='e.g. "Main account", "CBE — shop"'>
          <input
            className={inputCls}
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Account label"
            autoFocus={!bankId}
            aria-label="Account label"
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Bank name">
            <input
              className={inputCls}
              value={bankName}
              onChange={(e) => setBankName(e.target.value)}
              placeholder="Commercial Bank"
              aria-label="Bank name"
            />
          </Field>
          <Field label="Holder">
            <input
              className={inputCls}
              value={holderName}
              onChange={(e) => setHolderName(e.target.value)}
              placeholder="Full name"
              aria-label="Holder name"
            />
          </Field>
        </div>

        <Field label="Account number" hint="Shown masked in lists — reveal to copy the full number">
          <input
            className={cx(inputCls, "tracking-[1.5px] tabular-nums")}
            value={accountNumber}
            onChange={(e) => setAccountNumber(e.target.value)}
            placeholder="1000 2345 6789"
            inputMode="numeric"
            aria-label="Account number"
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="IBAN">
            <input
              className={inputCls}
              value={iban}
              onChange={(e) => setIban(e.target.value)}
              placeholder="ET29…"
              aria-label="IBAN"
            />
          </Field>
          <Field label="SWIFT / BIC">
            <input
              className={inputCls}
              value={swift}
              onChange={(e) => setSwift(e.target.value)}
              placeholder="CBETETAA"
              aria-label="SWIFT code"
            />
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="UPI ID">
            <input
              className={inputCls}
              value={upiId}
              onChange={(e) => setUpiId(e.target.value)}
              placeholder="name@bank"
              aria-label="UPI ID"
            />
          </Field>
          <Field label="Branch">
            <input
              className={inputCls}
              value={branch}
              onChange={(e) => setBranch(e.target.value)}
              placeholder="Bole branch"
              aria-label="Branch"
            />
          </Field>
        </div>

        <Field label="Balance" hint="Optional — use the calculator to compute it">
          <div className="flex gap-2">
            <input
              className={cx(inputCls, "tabular-nums")}
              value={balanceStr}
              onChange={(e) => setBalanceStr(e.target.value)}
              placeholder="0.00"
              inputMode="decimal"
              aria-label="Account balance"
            />
            <CalcButton onPick={(v) => setBalanceStr(String(v))} />
          </div>
        </Field>

        <Field label="Notes">
          <textarea
            className={cx(inputCls, "min-h-[80px] resize-y")}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="Anything special about this account…"
            aria-label="Account notes"
          />
        </Field>

        <AttachArea
          parentType="bank"
          draftId={draftId}
          ids={attachments}
          onAttached={onAttached}
          onEnsureDraft={ensureDraft}
        />
      </div>
    </Sheet>
  );
}
