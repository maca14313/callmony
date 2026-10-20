"use client";

import { useMemo, useState } from "react";
import { Inbox, UserRound } from "lucide-react";
import { useApp } from "@/lib/store";
import { displayName } from "@/lib/utils";
import { BottomSheet } from "./Sheet";
import { Avatar, SearchInput, cx } from "./primitives";

/** Pick a contact (or General) — used for owner assignment and moving entries. */
export function ContactPicker({
  open,
  onClose,
  onPick,
  allowGeneral = true,
  generalLabel = "General (no contact)",
  title = "Choose a contact",
}: {
  open: boolean;
  onClose: () => void;
  onPick: (ownerId: string, contactName?: string) => void;
  allowGeneral?: boolean;
  generalLabel?: string;
  title?: string;
}) {
  const allContacts = useApp((s) => s.contacts);
  const contacts = useMemo(() => allContacts.filter((c) => !c.deletedAt), [allContacts]);
  const [q, setQ] = useState("");

  const filtered = useMemo(() => {
    const query = q.toLowerCase().trim();
    const list = [...contacts].sort((a, b) => displayName(a).localeCompare(displayName(b)));
    if (!query) return list;
    return list.filter(
      (c) =>
        displayName(c).toLowerCase().includes(query) ||
        c.phones.some((p) => p.number.replace(/\s+/g, "").includes(query.replace(/\s+/g, "")))
    );
  }, [contacts, q]);

  return (
    <BottomSheet open={open} onClose={onClose} title={title}>
      <SearchInput value={q} onChange={setQ} placeholder="Search contacts…" autoFocus />
      <div className="mt-3 space-y-1">
        {allowGeneral && (
          <button
            type="button"
            onClick={() => {
              onPick("general");
              onClose();
            }}
            className="flex w-full items-center gap-3 rounded-2xl px-3 py-2.5 text-left transition hover:bg-surface2 active:scale-[0.98]"
          >
            <span className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-surface2 text-mute">
              <Inbox size={16} />
            </span>
            <span className="flex-1 text-[13px] font-semibold text-ink">{generalLabel}</span>
          </button>
        )}
        {filtered.map((c) => (
          <button
            key={c.id}
            type="button"
            onClick={() => {
              onPick(c.id, displayName(c));
              onClose();
            }}
            className={cx(
              "flex w-full items-center gap-3 rounded-2xl px-3 py-2 text-left transition hover:bg-surface2 active:scale-[0.98]"
            )}
          >
            <Avatar contact={c} size="sm" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13px] font-semibold text-ink">
                {displayName(c)} {c.favorite && <span className="text-brand">★</span>}
              </span>
              <span className="block truncate text-[11px] text-mute">
                {c.phones[0]?.number || c.emails[0]?.address || c.socials[0]?.handle || "—"}
              </span>
            </span>
            <UserRound size={14} className="shrink-0 text-line2" />
          </button>
        ))}
        {!filtered.length && (
          <p className="py-6 text-center text-xs text-mute">No contacts found</p>
        )}
      </div>
    </BottomSheet>
  );
}
