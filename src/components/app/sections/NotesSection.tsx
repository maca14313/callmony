"use client";

/* =========================================================================
   Task 2-c — NotesSection
   Notes for an owner: pinned first, then most recently updated. Card shows
   title, body preview, tags, pin indicator, attachment count and a pastel
   color strip. Tap → NoteEdit · long-press → Pin / Edit / Delete.
   ========================================================================= */

import { useMemo, useState } from "react";
import { Notebook, Paperclip, Pencil, Pin, PinOff, Plus, Trash2 } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { relTime } from "@/lib/utils";
import type { Note, OwnerId } from "@/lib/types";
import { BottomSheet } from "../kit/Sheet";
import { SwipeRow } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { toastOk, toastUndoLast } from "../kit/toast";
import { EmptyState, SectionLabel, cx } from "../kit/primitives";

export function NotesSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const confirm = useConfirm();
  const notesAll = useApp((s) => s.notes);
  const saveNote = useApp((s) => s.saveNote);
  const deleteNote = useApp((s) => s.deleteNote);
  const [sheetFor, setSheetFor] = useState<Note | null>(null);

  const notes = useMemo(
    () =>
      notesAll
        .filter((n) => n.ownerId === ownerId && !n.deletedAt)
        .sort(
          (a, b) =>
            (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || b.updatedAt - a.updatedAt
        ),
    [notesAll, ownerId]
  );

  const togglePin = (n: Note) => {
    saveNote({ id: n.id, ownerId: n.ownerId, pinned: !n.pinned });
    toastOk(n.pinned ? "Unpinned" : "Pinned to top");
    setSheetFor(null);
  };

  const remove = async (n: Note) => {
    const ok = await confirm({
      title: "Delete note?",
      message: `“${n.title || "Untitled note"}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    deleteNote(n.id);
    toastUndoLast();
    setSheetFor(null);
  };

  const addBtn = (
    <button
      type="button"
      onClick={() => ui.open({ type: "noteEdit", ownerId })}
      aria-label="New note"
      className="flex h-11 items-center gap-1 rounded-xl bg-brand px-3.5 text-[11px] font-semibold text-white shadow-key transition hover:bg-brandink active:scale-95"
    >
      <Plus size={13} /> New note
    </button>
  );

  if (!notes.length) {
    return (
      <section aria-label="Notes">
        <SectionLabel right={addBtn}>Notes</SectionLabel>
        <EmptyState
          icon={<Notebook size={21} />}
          title="No notes yet"
          hint="Meeting points, prices, IDs — keep them attached to this contact."
        />
      </section>
    );
  }

  return (
    <section aria-label="Notes" className="mt-1">
      <SectionLabel right={addBtn}>Notes · {notes.length}</SectionLabel>

      <div>
        {notes.map((n) => (
          <SwipeRow
            key={n.id}
            onTap={() => ui.open({ type: "noteEdit", noteId: n.id, ownerId })}
            onLongPress={() => setSheetFor(n)}
            className="mb-2.5"
          >
            <div className="relative overflow-hidden rounded-2xl bg-surface p-3.5 pl-4 shadow-key">
              {n.color && (
                <span
                  className="absolute inset-y-2.5 left-0 w-[5px] rounded-full"
                  style={{ background: n.color }}
                  aria-hidden
                />
              )}
              <div className="flex items-start justify-between gap-2">
                <p className="min-w-0 flex-1 truncate text-[13.5px] font-bold text-ink">
                  {n.title || "Untitled note"}
                </p>
                <div className="flex shrink-0 items-center gap-1.5 pt-0.5 text-mute">
                  {n.pinned && (
                    <Pin size={12} className="fill-current text-amber" aria-label="Pinned" />
                  )}
                  {n.attachments.length > 0 && (
                    <span className="flex items-center gap-0.5 text-[10px] font-bold">
                      <Paperclip size={11} aria-hidden />
                      {n.attachments.length}
                    </span>
                  )}
                  <span className="text-[10px] font-medium">{relTime(n.updatedAt)}</span>
                </div>
              </div>
              {n.body && (
                <p className="mt-1 line-clamp-2 text-[12px] leading-[1.5] text-mute">{n.body}</p>
              )}
              {n.tags.length > 0 && (
                <div className="mt-2 flex flex-wrap gap-1">
                  {n.tags.slice(0, 4).map((t) => (
                    <span
                      key={t}
                      className="rounded-full bg-surface2 px-2 py-0.5 text-[9.5px] font-semibold text-mute"
                    >
                      #{t}
                    </span>
                  ))}
                  {n.tags.length > 4 && (
                    <span className="rounded-full bg-surface2 px-2 py-0.5 text-[9.5px] font-semibold text-mute">
                      +{n.tags.length - 4}
                    </span>
                  )}
                </div>
              )}
            </div>
          </SwipeRow>
        ))}
      </div>

      {/* long-press actions */}
      <BottomSheet
        open={!!sheetFor}
        onClose={() => setSheetFor(null)}
        title={sheetFor?.title || "Untitled note"}
      >
        <div className="space-y-1.5 pb-2">
          <MenuRow
            icon={sheetFor?.pinned ? <PinOff size={16} /> : <Pin size={16} />}
            label={sheetFor?.pinned ? "Unpin note" : "Pin to top"}
            onClick={() => sheetFor && togglePin(sheetFor)}
          />
          <MenuRow
            icon={<Pencil size={16} />}
            label="Edit note"
            onClick={() => {
              const n = sheetFor;
              setSheetFor(null);
              if (n) ui.open({ type: "noteEdit", noteId: n.id, ownerId });
            }}
          />
          <MenuRow
            icon={<Trash2 size={16} />}
            label="Delete"
            danger
            onClick={() => sheetFor && void remove(sheetFor)}
          />
        </div>
      </BottomSheet>
    </section>
  );
}

function MenuRow({
  icon,
  label,
  onClick,
  danger,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "flex h-12 w-full items-center gap-3 rounded-2xl px-3 text-left transition active:scale-[0.98]",
        danger ? "text-neg hover:bg-negsoft" : "text-ink hover:bg-surface2"
      )}
    >
      <span
        className={cx(
          "flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px]",
          danger ? "bg-negsoft text-neg" : "bg-brandsoft text-brand"
        )}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1 truncate text-[13px] font-semibold">{label}</span>
    </button>
  );
}
