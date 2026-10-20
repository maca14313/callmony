"use client";

import { useMemo, useState } from "react";
import { Bell, CreditCard, Link2, ListChecks, Notebook, Phone, StickyNote, Unlink, Users } from "lucide-react";
import { useApp } from "@/lib/store";
import type { LinkRef } from "@/lib/types";
import { displayName } from "@/lib/utils";
import { useUI } from "@/lib/ui-store";
import { BottomSheet } from "./Sheet";
import { Button, Chip, SearchInput, cx } from "./primitives";

/* Links: notes, lists, reminders can be attached to contacts, entries, items — anywhere. */

const ICONS: Record<LinkRef["type"], typeof Phone> = {
  contact: Users,
  entry: CreditCard,
  list: ListChecks,
  note: Notebook,
  reminder: Bell,
  bank: CreditCard,
  item: StickyNote,
  link: Link2,
};

export function LinkChips({
  links,
  onChange,
  onOpen,
}: {
  links: LinkRef[];
  onChange?: (links: LinkRef[]) => void;
  onOpen?: (ref: LinkRef) => void;
}) {
  const app = useApp();
  const label = (l: LinkRef): string => {
    switch (l.type) {
      case "contact":
        return l.label || displayName(app.contacts.find((c) => c.id === l.id));
      case "entry":
        return l.label || "Ledger entry";
      case "list":
        return l.label || app.lists.find((x) => x.id === l.id)?.title || "List";
      case "note":
        return l.label || app.notes.find((x) => x.id === l.id)?.title || "Note";
      case "reminder":
        return l.label || app.reminders.find((x) => x.id === l.id)?.title || "Reminder";
      case "bank":
        return l.label || app.banks.find((x) => x.id === l.id)?.label || "Account";
      default:
        return l.label || "Link";
    }
  };
  if (!links.length) return null;
  return (
    <div className="flex flex-wrap gap-1.5">
      {links.map((l, i) => {
        const Icon = ICONS[l.type] || Link2;
        return (
          <span
            key={`${l.type}-${l.id}`}
            className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface py-1 pl-2.5 pr-1.5 text-[11px] font-semibold text-ink shadow-key"
          >
            <Icon size={12} className="text-brand" />
            <button
              type="button"
              className="max-w-[140px] truncate"
              onClick={() => onOpen?.(l)}
            >
              {label(l)}
            </button>
            {onChange && (
              <button
                type="button"
                aria-label={`Unlink ${label(l)}`}
                onClick={() => onChange(links.filter((x) => !(x.type === l.type && x.id === l.id)))}
                className="flex h-4 w-4 items-center justify-center rounded-full bg-surface2 text-mute hover:text-neg"
              >
                <Unlink size={9} />
              </button>
            )}
          </span>
        );
      })}
    </div>
  );
}

export function LinkPicker({
  open,
  onClose,
  onPick,
  exclude,
}: {
  open: boolean;
  onClose: () => void;
  onPick: (ref: LinkRef) => void;
  exclude?: LinkRef[];
}) {
  const app = useApp();
  const [tab, setTab] = useState<"contact" | "note" | "list" | "reminder" | "bank">("contact");
  const [q, setQ] = useState("");

  const excluded = (type: LinkRef["type"], id: string) =>
    (exclude || []).some((e) => e.type === type && e.id === id);

  const contacts = useMemo(
    () =>
      app.contacts
        .filter((c) => !c.deletedAt && displayName(c).toLowerCase().includes(q.toLowerCase()))
        .slice(0, 30),
    [app.contacts, q]
  );
  const notes = app.notes.filter((n) => !n.deletedAt && n.title.toLowerCase().includes(q.toLowerCase()));
  const lists = app.lists.filter((l) => !l.deletedAt && l.title.toLowerCase().includes(q.toLowerCase()));
  const reminders = app.reminders.filter((r) => !r.deletedAt && r.title.toLowerCase().includes(q.toLowerCase()));
  const banks = app.banks.filter((b) => !b.deletedAt && b.label.toLowerCase().includes(q.toLowerCase()));

  const pick = (type: LinkRef["type"], id: string, label?: string) => {
    onPick({ type, id, label });
    onClose();
    setQ("");
  };

  return (
    <BottomSheet open={open} onClose={onClose} title="Attach to…">
      <div className="mb-3 flex gap-1.5 overflow-x-auto pb-1 scrollbar-hide">
        <Chip active={tab === "contact"} onClick={() => setTab("contact")}>Contacts</Chip>
        <Chip active={tab === "note"} onClick={() => setTab("note")}>Notes</Chip>
        <Chip active={tab === "list"} onClick={() => setTab("list")}>Lists</Chip>
        <Chip active={tab === "reminder"} onClick={() => setTab("reminder")}>Reminders</Chip>
        <Chip active={tab === "bank"} onClick={() => setTab("bank")}>Accounts</Chip>
      </div>
      <SearchInput value={q} onChange={setQ} placeholder="Search to link…" />
      <div className="mt-3 space-y-1">
        {tab === "contact" &&
          contacts.map((c) => (
            <Row
              key={c.id}
              icon={<Users size={16} />}
              title={displayName(c)}
              sub={c.phones[0]?.number}
              onClick={() => pick("contact", c.id, displayName(c))}
              disabled={excluded("contact", c.id)}
            />
          ))}
        {tab === "note" &&
          notes.map((n) => (
            <Row
              key={n.id}
              icon={<Notebook size={16} />}
              title={n.title}
              sub={n.body.slice(0, 40)}
              onClick={() => pick("note", n.id, n.title)}
              disabled={excluded("note", n.id)}
            />
          ))}
        {tab === "list" &&
          lists.map((l) => (
            <Row
              key={l.id}
              icon={<ListChecks size={16} />}
              title={l.title}
              sub={`${l.items.length} items`}
              onClick={() => pick("list", l.id, l.title)}
              disabled={excluded("list", l.id)}
            />
          ))}
        {tab === "reminder" &&
          reminders.map((r) => (
            <Row
              key={r.id}
              icon={<Bell size={16} />}
              title={r.title}
              sub={new Date(r.dueAt).toLocaleString()}
              onClick={() => pick("reminder", r.id, r.title)}
              disabled={excluded("reminder", r.id)}
            />
          ))}
        {tab === "bank" &&
          banks.map((b) => (
            <Row
              key={b.id}
              icon={<CreditCard size={16} />}
              title={b.label}
              sub={b.bankName}
              onClick={() => pick("bank", b.id, b.label)}
              disabled={excluded("bank", b.id)}
            />
          ))}
        {((tab === "contact" && !contacts.length) ||
          (tab === "note" && !notes.length) ||
          (tab === "list" && !lists.length) ||
          (tab === "reminder" && !reminders.length) ||
          (tab === "bank" && !banks.length)) && (
          <p className="py-6 text-center text-xs text-mute">Nothing found</p>
        )}
      </div>
    </BottomSheet>
  );
}

function Row({
  icon,
  title,
  sub,
  onClick,
  disabled,
}: {
  icon: React.ReactNode;
  title: string;
  sub?: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cx(
        "flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition active:scale-[0.98]",
        disabled ? "opacity-40" : "hover:bg-surface2"
      )}
    >
      <span className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-semibold text-ink">{title}</span>
        {sub && <span className="block truncate text-[11px] text-mute">{sub}</span>}
      </span>
    </button>
  );
}

/** Opens a linked target (used by LinkChips onOpen). */
export function useOpenLink() {
  const ui = useUI();
  return (l: LinkRef) => {
    switch (l.type) {
      case "contact":
        ui.open({ type: "contact", contactId: l.id });
        break;
      case "list":
        ui.open({ type: "list", listId: l.id });
        break;
      case "note":
        ui.open({ type: "noteEdit", noteId: l.id, ownerId: "general" });
        break;
      case "reminder":
        ui.open({ type: "reminderEdit", reminderId: l.id, ownerId: "general" });
        break;
      case "bank":
        ui.open({ type: "bankEdit", bankId: l.id, ownerId: "general" });
        break;
      default:
        break;
    }
  };
}

/** Button that opens LinkPicker and appends the chosen link. */
export function AddLinkButton({
  links,
  onChange,
  label = "Link",
}: {
  links: LinkRef[];
  onChange: (links: LinkRef[]) => void;
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant="soft" size="sm" onClick={() => setOpen(true)}>
        <Link2 size={13} /> {label}
      </Button>
      <LinkPicker
        open={open}
        onClose={() => setOpen(false)}
        exclude={links}
        onPick={(ref) => onChange([...links, ref])}
      />
    </>
  );
}
