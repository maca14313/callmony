"use client";

import { useMemo, useState } from "react";
import {
  Bell,
  Check,
  ChevronDown,
  Landmark,
  Link2,
  ListChecks,
  Paperclip,
  Plus,
  StickyNote,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import type { LinkRef, OwnerId } from "@/lib/types";
import { useT } from "@/lib/i18n";
import { openExternal } from "@/lib/share";
import { displayName, uid } from "@/lib/utils";
import { BottomSheet } from "./Sheet";
import { Button, cx, inputCls, SearchInput, Segmented } from "./primitives";
import { toastErr, toastOk } from "./toast";

/* ------------------------------------------------------------------ */
/* Entity links — attach banks, lists, notes, reminders, owners        */
/* (many) and web links to ledger entries, groups, payments, lists…    */
/* Replaces file attachments.                                          */
/* ------------------------------------------------------------------ */

type PickTab = "contacts" | "lists" | "notes" | "reminders" | "banks" | "link";

const REF_ICON = {
  contact: UserRound,
  list: ListChecks,
  note: StickyNote,
  reminder: Bell,
  bank: Landmark,
  link: Link2,
  entry: Paperclip,
  item: Paperclip,
} as const;

export function useRefMeta() {
  const contacts = useApp((s) => s.contacts);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  return (ref: LinkRef): { label: string; sub?: string } => {
    switch (ref.type) {
      case "contact": {
        const c = contacts.find((x) => x.id === ref.id && !x.deletedAt);
        return { label: ref.label || (c ? displayName(c) : "Contact"), sub: "Contact" };
      }
      case "list": {
        const l = lists.find((x) => x.id === ref.id && !x.deletedAt);
        return { label: ref.label || l?.title || "List", sub: "List" };
      }
      case "note": {
        const n = notes.find((x) => x.id === ref.id && !x.deletedAt);
        return { label: ref.label || n?.title || "Note", sub: "Note" };
      }
      case "reminder": {
        const r = reminders.find((x) => x.id === ref.id && !x.deletedAt);
        return { label: ref.label || r?.title || "Reminder", sub: "Reminder" };
      }
      case "bank": {
        const b = banks.find((x) => x.id === ref.id && !x.deletedAt);
        return { label: ref.label || b?.bankName || b?.label || "Bank", sub: "Bank" };
      }
      case "link":
        return { label: ref.label || ref.url || "Link", sub: ref.url };
      default:
        return { label: ref.label || "Attached", sub: undefined };
    }
  };
}

function openRef(ref: LinkRef) {
  const ui = useUI.getState();
  const app = useApp.getState();
  switch (ref.type) {
    case "contact":
      if (ref.id !== "general") ui.open({ type: "contact", contactId: ref.id });
      break;
    case "list":
      ui.open({ type: "list", listId: ref.id });
      break;
    case "note": {
      const n = app.notes.find((x) => x.id === ref.id);
      ui.open({ type: "noteEdit", noteId: ref.id, ownerId: n?.ownerId ?? "general" });
      break;
    }
    case "reminder": {
      const r = app.reminders.find((x) => x.id === ref.id);
      ui.open({ type: "reminderEdit", reminderId: ref.id, ownerId: r?.ownerId ?? "general" });
      break;
    }
    case "bank": {
      const b = app.banks.find((x) => x.id === ref.id);
      ui.open({ type: "bankEdit", bankId: ref.id, ownerId: b?.ownerId ?? "general" });
      break;
    }
    case "link":
      if (ref.url) openExternal(ref.url);
      break;
  }
}

/** Compact chips for the attached entities. Editable when onChange is given. */
export function EntityRefChips({
  refs,
  onChange,
  className,
}: {
  refs?: LinkRef[];
  onChange?: (refs: LinkRef[]) => void;
  className?: string;
}) {
  const meta = useRefMeta();
  const t = useT();
  if (!refs || refs.length === 0) return null;
  return (
    <div className={cx("flex flex-wrap gap-1.5", className)}>
      {refs.map((ref) => {
        const Icon = REF_ICON[ref.type] ?? Paperclip;
        const m = meta(ref);
        return (
          <span
            key={`${ref.type}-${ref.id}`}
            className={cx(
              "inline-flex max-w-full items-center gap-1.5 rounded-full border border-line bg-surface2 py-1 pl-2 text-[11px] font-semibold text-ink shadow-key",
              onChange ? "pr-1" : "pr-2.5"
            )}
          >
            <button
              type="button"
              onClick={() => openRef(ref)}
              className="flex min-w-0 items-center gap-1.5"
              aria-label={`Open ${m.label}`}
            >
              <Icon size={12} className="shrink-0 text-brand" />
              <span className="max-w-[130px] truncate">{m.label}</span>
            </button>
            {onChange && (
              <button
                type="button"
                onClick={() => onChange((refs || []).filter((x) => !(x.type === ref.type && x.id === ref.id)))}
                className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-line2/60 text-mute transition hover:bg-neg hover:text-white"
                aria-label={`Remove ${m.label}`}
              >
                <X size={9} strokeWidth={3} />
              </button>
            )}
          </span>
        );
      })}
      {onChange && refs.length === 0 && <span className="text-[10px] text-mute">{t("att.attached")}</span>}
    </div>
  );
}

/** Attach button + chips + picker sheet.
 *  Usage: <AttachEntities refs={refs} onChange={setRefs} ownerId={ownerId} />
 *  ownerId (default "general") is where newly created lists / notes / reminders
 *  / banks land when using the "Create new" affordance inside the picker. */
export function AttachEntities({
  refs,
  onChange,
  compact,
  ownerId = "general",
}: {
  refs: LinkRef[];
  onChange: (refs: LinkRef[]) => void;
  compact?: boolean;
  ownerId?: OwnerId;
}) {
  const t = useT();
  const [open, setOpen] = useState(false);

  return (
    <div className="space-y-2">
      {!compact && <EntityRefChips refs={refs} onChange={onChange} />}
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="flex items-center gap-1.5 rounded-full border border-dashed border-brand/50 bg-brandsoft/60 px-3 py-1.5 text-[11px] font-semibold text-brand transition active:scale-95"
      >
        <Paperclip size={12} />
        {t("att.attach")}
      </button>
      {open && (
        <EntityPickerSheet
          open
          onClose={() => setOpen(false)}
          refs={refs}
          ownerId={ownerId}
          onDone={(next) => {
            onChange(next);
            setOpen(false);
            toastOk(t("att.attached"));
          }}
        />
      )}
    </div>
  );
}

function sameRef(a: LinkRef, b: LinkRef) {
  return a.type === b.type && a.id === b.id;
}

function EntityPickerSheet({
  open,
  onClose,
  refs,
  ownerId = "general",
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  refs: LinkRef[];
  ownerId?: OwnerId;
  onDone: (refs: LinkRef[]) => void;
}) {
  const t = useT();
  const contacts = useApp((s) => s.contacts);
  const lists = useApp((s) => s.lists);
  const notes = useApp((s) => s.notes);
  const reminders = useApp((s) => s.reminders);
  const banks = useApp((s) => s.banks);
  const saveContact = useApp((s) => s.saveContact);
  const saveList = useApp((s) => s.saveList);
  const saveNote = useApp((s) => s.saveNote);
  const saveReminder = useApp((s) => s.saveReminder);
  const saveBank = useApp((s) => s.saveBank);
  const [tab, setTab] = useState<PickTab>("contacts");
  const [query, setQuery] = useState("");
  const [picked, setPicked] = useState<LinkRef[]>(refs);
  const [linkLabel, setLinkLabel] = useState("");
  const [linkUrl, setLinkUrl] = useState("");
  /* "Create new" inline mini-form state */
  const [createOpen, setCreateOpen] = useState(false);
  const [cName, setCName] = useState("");
  const [cPhone, setCPhone] = useState("");
  const [cTitle, setCTitle] = useState("");
  const [cBody, setCBody] = useState("");
  const [cNumber, setCNumber] = useState("");
  const [cHolder, setCHolder] = useState("");

  const newLabel =
    tab === "contacts"
      ? t("att.newContact")
      : tab === "lists"
        ? t("att.newList")
        : tab === "notes"
          ? t("att.newNote")
          : tab === "reminders"
            ? t("att.newReminder")
            : t("att.newBank");

  const resetCreateForm = () => {
    setCName("");
    setCPhone("");
    setCTitle("");
    setCBody("");
    setCNumber("");
    setCHolder("");
  };

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const match = (s: string) => !q || s.toLowerCase().includes(q);
    switch (tab) {
      case "contacts":
        return contacts
          .filter((c) => !c.deletedAt && match(displayName(c)))
          .map((c) => ({ ref: { type: "contact" as const, id: c.id, label: displayName(c) }, key: c.id }));
      case "lists":
        return lists
          .filter((l) => !l.deletedAt && !l.isTemplate && match(l.title))
          .map((l) => ({ ref: { type: "list" as const, id: l.id, label: l.title }, key: l.id }));
      case "notes":
        return notes
          .filter((n) => !n.deletedAt && match(n.title))
          .map((n) => ({ ref: { type: "note" as const, id: n.id, label: n.title }, key: n.id }));
      case "reminders":
        return reminders
          .filter((r) => !r.deletedAt && match(r.title))
          .map((r) => ({ ref: { type: "reminder" as const, id: r.id, label: r.title }, key: r.id }));
      case "banks":
        return banks
          .filter((b) => !b.deletedAt && match(b.bankName || b.label || ""))
          .map((b) => ({
            ref: { type: "bank" as const, id: b.id, label: b.bankName || b.label || "Bank" },
            key: b.id,
          }));
      default:
        return [];
    }
  }, [tab, query, contacts, lists, notes, reminders, banks]);

  const toggle = (ref: LinkRef) =>
    setPicked((p) => (p.some((x) => sameRef(x, ref)) ? p.filter((x) => !sameRef(x, ref)) : [...p, ref]));

  const addLink = () => {
    const url = linkUrl.trim();
    if (!url) return;
    const full = /^https?:\/\//i.test(url) ? url : `https://${url}`;
    setPicked((p) => [
      ...p,
      { type: "link", id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, label: linkLabel.trim() || url, url: full },
    ]);
    setLinkLabel("");
    setLinkUrl("");
  };

  /** Create a brand-new entity for the current tab and immediately pick it
   *  so Done attaches it to the parent (entry / group / payment / list…). */
  const createAndPick = () => {
    let ref: LinkRef | null = null;
    if (tab === "contacts") {
      const firstName = cName.trim();
      const number = cPhone.trim();
      if (!firstName && !number) {
        toastErr(t("ce.atLeastOne"));
        return;
      }
      const c = saveContact({
        firstName,
        phones: number ? [{ id: uid(), label: "mobile", number }] : [],
      });
      ref = { type: "contact", id: c.id, label: displayName(c) };
    } else if (tab === "lists") {
      const title = cTitle.trim();
      if (!title) {
        toastErr(t("ls.giveListTitle"));
        return;
      }
      const l = saveList({ ownerId, title, kind: "simple" });
      ref = { type: "list", id: l.id, label: l.title };
    } else if (tab === "notes") {
      const title = cTitle.trim();
      if (!title) {
        toastErr(t("nt.titleRequired"));
        return;
      }
      const n = saveNote({ ownerId, title, body: cBody.trim() });
      ref = { type: "note", id: n.id, label: n.title };
    } else if (tab === "reminders") {
      const title = cTitle.trim();
      if (!title) {
        toastErr(t("nt.titleRequired"));
        return;
      }
      const d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(9, 0, 0, 0);
      const r = saveReminder({ ownerId, title, kind: "custom", dueAt: d.getTime() });
      ref = { type: "reminder", id: r.id, label: r.title };
    } else if (tab === "banks") {
      const bankName = cTitle.trim();
      const accountNumber = cNumber.trim();
      if (!bankName) {
        toastErr(t("bk.bankNameRequired"));
        return;
      }
      if (!accountNumber) {
        toastErr(t("bk.accountNumberRequired"));
        return;
      }
      const b = saveBank({
        ownerId,
        label: bankName,
        bankName,
        accountNumber,
        holderName: cHolder.trim() || undefined,
      });
      ref = { type: "bank", id: b.id, label: b.bankName || b.label };
    }
    if (ref) {
      setPicked((p) => (p.some((x) => sameRef(x, ref)) ? p : [...p, ref]));
      toastOk(t("att.attached"));
    }
    resetCreateForm();
    setCreateOpen(false);
  };

  return (
    <BottomSheet open={open} onClose={onClose} title={t("att.attach")} zIndex={75}>
      <div className="space-y-3 pb-2">
        <Segmented
          value={tab}
          onChange={(v) => {
            setTab(v as PickTab);
            setCreateOpen(false);
            resetCreateForm();
          }}
          options={[
            { value: "contacts", label: <Users size={13} /> },
            { value: "lists", label: <ListChecks size={13} /> },
            { value: "notes", label: <StickyNote size={13} /> },
            { value: "reminders", label: <Bell size={13} /> },
            { value: "banks", label: <Landmark size={13} /> },
            { value: "link", label: <Link2 size={13} /> },
          ]}
        />

        {tab !== "link" ? (
          <>
            {/* create a brand-new entity of this tab's type and attach it */}
            <button
              type="button"
              onClick={() => setCreateOpen((v) => !v)}
              aria-expanded={createOpen}
              className="flex w-full items-center gap-2.5 rounded-2xl border border-dashed border-brand/50 bg-brandsoft/40 px-3 py-2.5 text-left transition active:scale-[0.98]"
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-[12px] bg-brand text-white" aria-hidden>
                <Plus size={15} />
              </span>
              <span className="flex-1 text-[13px] font-semibold text-brand">{newLabel}</span>
              <ChevronDown
                size={15}
                className={cx("shrink-0 text-brand transition-transform", createOpen && "rotate-180")}
                aria-hidden
              />
            </button>
            {createOpen && (
              <div className="space-y-2 rounded-2xl border border-line bg-surface p-3">
                {tab === "contacts" && (
                  <>
                    <input
                      className={inputCls}
                      placeholder={t("ce.firstName")}
                      value={cName}
                      onChange={(e) => setCName(e.target.value)}
                      aria-label={t("ce.firstName")}
                    />
                    <input
                      className={inputCls}
                      placeholder={t("ce.phonePlaceholder")}
                      inputMode="tel"
                      value={cPhone}
                      onChange={(e) => setCPhone(e.target.value)}
                      aria-label={t("ce.phonePlaceholder")}
                    />
                  </>
                )}
                {tab === "lists" && (
                  <input
                    className={inputCls}
                    placeholder={t("ls.listTitle")}
                    value={cTitle}
                    onChange={(e) => setCTitle(e.target.value)}
                    aria-label={t("ls.listTitle")}
                  />
                )}
                {tab === "notes" && (
                  <>
                    <input
                      className={inputCls}
                      placeholder={t("nt.noteTitle")}
                      value={cTitle}
                      onChange={(e) => setCTitle(e.target.value)}
                      aria-label={t("nt.noteTitle")}
                    />
                    <textarea
                      className={cx(inputCls, "resize-none")}
                      rows={2}
                      placeholder={t("nt.writeAnything")}
                      value={cBody}
                      onChange={(e) => setCBody(e.target.value)}
                      aria-label={t("nt.noteBody")}
                    />
                  </>
                )}
                {tab === "reminders" && (
                  <input
                    className={inputCls}
                    placeholder={t("rm.reminderTitle")}
                    value={cTitle}
                    onChange={(e) => setCTitle(e.target.value)}
                    aria-label={t("rm.reminderTitle")}
                  />
                )}
                {tab === "banks" && (
                  <>
                    <input
                      className={inputCls}
                      placeholder={t("bk.bankPlaceholder")}
                      value={cTitle}
                      onChange={(e) => setCTitle(e.target.value)}
                      aria-label={t("bk.bankName")}
                    />
                    <input
                      className={inputCls}
                      placeholder={t("bk.numberPlaceholder")}
                      inputMode="numeric"
                      value={cNumber}
                      onChange={(e) => setCNumber(e.target.value)}
                      aria-label={t("bk.accountNumber")}
                    />
                    <input
                      className={inputCls}
                      placeholder={t("bk.holderName")}
                      value={cHolder}
                      onChange={(e) => setCHolder(e.target.value)}
                      aria-label={t("bk.holderName")}
                    />
                  </>
                )}
                <Button variant="brand" size="sm" className="w-full" onClick={createAndPick}>
                  <Plus size={14} /> {t("att.createAndAttach")}
                </Button>
              </div>
            )}

            <SearchInput value={query} onChange={setQuery} placeholder={t("misc.searchPlaceholder")} />
            <div className="app-scroll max-h-64 overflow-y-auto">
              {tab === "contacts" && (
                <button
                  onClick={() => toggle({ type: "contact", id: "general", label: t("hdr.general") })}
                  className="mb-1.5 flex w-full items-center gap-2.5 rounded-2xl border border-line bg-brandsoft/50 px-3 py-2.5 text-left"
                >
                  <span className="flex h-8 w-8 items-center justify-center rounded-[12px] bg-brand text-white">
                    <Plus size={15} />
                  </span>
                  <span className="flex-1 text-[13px] font-semibold text-ink">{t("hdr.general")}</span>
                  {picked.some((x) => x.type === "contact" && x.id === "general") && (
                    <Check size={16} className="text-brand" />
                  )}
                </button>
              )}
              {filtered.length === 0 && (
                <p className="py-6 text-center text-[12px] text-mute">{t("misc.noResults")}</p>
              )}
              {filtered.map(({ ref, key }) => {
                const active = picked.some((x) => sameRef(x, ref));
                return (
                  <button
                    key={key}
                    onClick={() => toggle(ref)}
                    className={cx(
                      "mb-1.5 flex w-full items-center gap-2.5 rounded-2xl border px-3 py-2.5 text-left transition active:scale-[0.98]",
                      active ? "border-brand/40 bg-brandsoft" : "border-line bg-surface"
                    )}
                  >
                    <span className="flex h-8 w-8 items-center justify-center rounded-[12px] bg-surface2 text-brand">
                      {(() => {
                        const Icon = REF_ICON[ref.type as keyof typeof REF_ICON] ?? Paperclip;
                        return <Icon size={14} />;
                      })()}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">{ref.label}</span>
                    {active ? (
                      <Check size={16} className="shrink-0 text-brand" />
                    ) : (
                      <span className="h-4 w-4 shrink-0 rounded-full border border-line2" />
                    )}
                  </button>
                );
              })}
            </div>
          </>
        ) : (
          <div className="space-y-2.5 rounded-2xl border border-line bg-surface p-3">
            <input
              className={inputCls}
              placeholder={t("att.labelPlaceholder")}
              value={linkLabel}
              onChange={(e) => setLinkLabel(e.target.value)}
              aria-label={t("att.labelPlaceholder")}
            />
            <input
              className={inputCls}
              placeholder={t("att.urlPlaceholder")}
              inputMode="url"
              value={linkUrl}
              onChange={(e) => setLinkUrl(e.target.value)}
              aria-label={t("att.urlPlaceholder")}
            />
            <Button variant="brand" size="sm" className="w-full" onClick={addLink}>
              <Plus size={14} /> {t("act.add")}
            </Button>
          </div>
        )}

        {picked.length > 0 && (
          <div className="rounded-2xl border border-line bg-surface2/60 p-2.5">
            <p className="mb-1.5 px-1 text-[10px] font-semibold uppercase tracking-[1.5px] text-mute">
              {t("att.attached")} · {picked.length}
            </p>
            <div className="flex flex-wrap gap-1.5">
              {picked.map((ref) => {
                const Icon = REF_ICON[ref.type as keyof typeof REF_ICON] ?? Paperclip;
                return (
                  <button
                    key={`${ref.type}-${ref.id}`}
                    onClick={() => setPicked((p) => p.filter((x) => !sameRef(x, ref)))}
                    className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface py-1 pl-2 pr-1 text-[11px] font-semibold text-ink"
                  >
                    <Icon size={11} className="text-brand" />
                    <span className="max-w-[120px] truncate">{ref.label || ref.id}</span>
                    <X size={10} />
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <Button variant="brand" size="lg" className="w-full" onClick={() => onDone(picked)}>
          <Check size={16} /> {t("act.done")}
        </Button>
      </div>
    </BottomSheet>
  );
}
