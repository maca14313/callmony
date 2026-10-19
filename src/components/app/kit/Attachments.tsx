"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  Download,
  File as FileIcon,
  FileText,
  Minus,
  Paperclip,
  Plus,
  Share2,
  Trash2,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import type { AttachmentMeta, AttachmentParentType, ID, OwnerId } from "@/lib/types";
import { bytesStr, downloadBlob } from "@/lib/utils";
import { shareFiles } from "@/lib/share";
import { Button, IconButton, cx } from "./primitives";
import { useConfirm } from "./Confirm";
import { toastOk } from "./toast";

/** 📎 Attach button — accepts images (auto-compressed), PDFs and any file. */
export function AttachButton({
  parentType,
  parentId,
  onAttached,
  compact,
  label = "Attach",
}: {
  parentType: AttachmentParentType;
  parentId: ID;
  onAttached?: (meta: AttachmentMeta) => void;
  compact?: boolean;
  label?: string;
}) {
  const ref = useRef<HTMLInputElement>(null);
  const addAttachment = useApp((s) => s.addAttachment);
  const [busy, setBusy] = useState(false);

  return (
    <>
      <input
        ref={ref}
        type="file"
        multiple
        accept="image/*,application/pdf,*"
        className="hidden"
        onChange={async (e) => {
          const files = Array.from(e.target.files || []);
          e.target.value = "";
          if (!files.length) return;
          setBusy(true);
          for (const f of files) {
            const meta = await addAttachment(f, parentType, parentId);
            if (meta) onAttached?.(meta);
          }
          setBusy(false);
          toastOk(files.length > 1 ? `${files.length} files attached` : "Attached");
        }}
      />
      <Button
        variant="soft"
        size={compact ? "sm" : "md"}
        disabled={busy}
        onClick={() => ref.current?.click()}
      >
        <Paperclip size={compact ? 13 : 15} className={busy ? "animate-pulse" : ""} />
        {label}
      </Button>
    </>
  );
}

/** Thumb grid of attachments for one parent, with remove + open in viewer. */
export function AttachmentStrip({
  parentType,
  parentId,
  allowRemove = true,
  ids,
}: {
  parentType?: AttachmentParentType;
  parentId?: ID;
  allowRemove?: boolean;
  ids?: ID[];
}) {
  const confirm = useConfirm();
  const [viewing, setViewing] = useState<AttachmentMeta | null>(null);
  const allAttachments = useApp((s) => s.attachments);
  const attachments = useMemo(
    () =>
      ids
        ? allAttachments.filter((a) => ids.includes(a.id))
        : allAttachments.filter((a) => a.parentType === parentType && a.parentId === parentId),
    [ids, allAttachments, parentType, parentId]
  );
  const removeAttachment = useApp((s) => s.removeAttachment);

  if (!attachments.length) return null;

  return (
    <>
      <div className="app-scroll flex gap-2.5 overflow-x-auto pb-1">
        {attachments.map((a) => (
          <div key={a.id} className="group relative shrink-0">
            <button
              type="button"
              onClick={() => setViewing(a)}
              className="flex h-[76px] w-[76px] flex-col items-center justify-center overflow-hidden rounded-[18px] border border-line bg-surface2 shadow-key transition active:scale-95"
              aria-label={`Open ${a.name}`}
            >
              {a.kind === "image" && a.thumb ? (
                <img src={a.thumb} alt={a.name} className="h-full w-full object-cover" />
              ) : a.kind === "pdf" ? (
                <FileText size={26} className="text-neg" />
              ) : (
                <FileIcon size={24} className="text-mute" />
              )}
            </button>
            {allowRemove && (
              <button
                type="button"
                aria-label={`Remove ${a.name}`}
                onClick={async () => {
                  const ok = await confirm({
                    title: "Remove attachment?",
                    message: `${a.name} (${bytesStr(a.size)}) will be removed.`,
                    confirmLabel: "Remove",
                    tone: "danger",
                  });
                  if (ok) {
                    await removeAttachment(a.id);
                    toastOk("Attachment removed");
                  }
                }}
                className="absolute -right-1.5 -top-1.5 flex h-6 w-6 items-center justify-center rounded-full border-2 border-canvas bg-neg text-white shadow-key transition active:scale-90"
              >
                <X size={11} strokeWidth={3} />
              </button>
            )}
            <p className="mt-1 w-[76px] truncate text-center text-[9px] text-mute">{a.name}</p>
          </div>
        ))}
      </div>
      <AnimatePresence>
        {viewing && <AttachmentViewer meta={viewing} onClose={() => setViewing(null)} />}
      </AnimatePresence>
    </>
  );
}

/** Full-screen in-frame viewer: images zoom, PDFs preview, files download/share. */
export function AttachmentViewer({ meta, onClose }: { meta: AttachmentMeta; onClose: () => void }) {
  const blob = useApp((s) => s.attachmentBlob);
  const [url, setUrl] = useState<string | null>(null);
  const [scale, setScale] = useState(1);
  const [loading, setLoading] = useState(true);

  // load blob → object URL
  useEffect(() => {
    let active = true;
    void blob(meta.id).then((b) => {
      if (!active) return;
      if (b) setUrl(URL.createObjectURL(b));
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [blob, meta.id]);

  const getFile = async (): Promise<File | null> => {
    const b = await blob(meta.id);
    return b ? new File([b], meta.name, { type: b.type }) : null;
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="absolute inset-0 flex flex-col"
      style={{ zIndex: 95, background: "rgba(10,16,15,0.94)" }}
      role="dialog"
      aria-label={`Viewing ${meta.name}`}
    >
      <div className="flex items-center justify-between px-4 py-3 text-white">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{meta.name}</p>
          <p className="text-[10px] text-white/60">
            {meta.kind.toUpperCase()} · {bytesStr(meta.size)}
          </p>
        </div>
        <IconButton
          label="Close viewer"
          onClick={onClose}
          className="border-white/20 bg-white/10 text-white hover:bg-white/20"
        >
          <X size={18} />
        </IconButton>
      </div>

      <div className="app-scroll relative flex flex-1 items-center justify-center overflow-auto p-4">
        {loading && <p className="text-xs text-white/60">Loading…</p>}
        {!loading && !url && <p className="text-xs text-white/60">File not found (was it removed?)</p>}
        {url && meta.kind === "image" && (
          <div className="relative flex items-center justify-center">
            <img
              src={url}
              alt={meta.name}
              style={{ transform: `scale(${scale})` }}
              className="max-h-full max-w-full rounded-xl object-contain transition-transform duration-200"
            />
            <div className="absolute bottom-3 flex items-center gap-2 rounded-full bg-black/60 px-2 py-1 backdrop-blur">
              <button
                aria-label="Zoom out"
                onClick={() => setScale((s) => Math.max(0.4, +(s - 0.25).toFixed(2)))}
                className="flex h-8 w-8 items-center justify-center rounded-full text-white hover:bg-white/15"
              >
                <Minus size={15} />
              </button>
              <span className="w-12 text-center text-[11px] font-semibold tabular-nums text-white">
                {Math.round(scale * 100)}%
              </span>
              <button
                aria-label="Zoom in"
                onClick={() => setScale((s) => Math.min(4, +(s + 0.25).toFixed(2)))}
                className="flex h-8 w-8 items-center justify-center rounded-full text-white hover:bg-white/15"
              >
                <Plus size={15} />
              </button>
            </div>
          </div>
        )}
        {url && meta.kind === "pdf" && (
          <iframe
            src={url}
            title={meta.name}
            className="h-full w-full rounded-xl bg-white"
          />
        )}
        {url && meta.kind === "file" && (
          <div className="flex flex-col items-center gap-3 rounded-3xl bg-white/10 p-8 text-center text-white">
            <FileIcon size={44} className="opacity-80" />
            <p className="max-w-[220px] break-all text-sm font-semibold">{meta.name}</p>
            <p className="text-xs text-white/60">{bytesStr(meta.size)} · {meta.mimeType}</p>
          </div>
        )}
      </div>

      <div className="flex items-center justify-center gap-3 border-t border-white/10 px-4 py-4 pb-[max(16px,env(safe-area-inset-bottom))]">
        <Button
          variant="soft"
          size="md"
          onClick={async () => {
            const f = await getFile();
            if (f) downloadBlob(f, f.name);
          }}
        >
          <Download size={15} /> Download
        </Button>
        <Button
          variant="soft"
          size="md"
          onClick={async () => {
            const f = await getFile();
            if (f) {
              const r = await shareFiles(f.name, [f]);
              if (r !== "shared") toastOk("Sharing not supported — use Download instead");
            }
          }}
        >
          <Share2 size={15} /> Share
        </Button>
      </div>
    </motion.div>
  );
}

/** Files tab content — every attachment belonging to an owner's entities. */
export function FilesOfOwner({ ownerId }: { ownerId: OwnerId }) {
  const app = useApp();
  const [viewing, setViewing] = useState<AttachmentMeta | null>(null);

  const idsOf = (list: { attachments?: ID[]; items?: { attachments?: ID[] }[]; ownerId?: OwnerId; id?: ID }[]) => {
    const out: ID[] = [];
    list.forEach((x) => {
      if (x.ownerId !== ownerId) return;
      (x.attachments || []).forEach((id) => out.push(id));
      (x.items || []).forEach((i) => (i.attachments || []).forEach((id) => out.push(id)));
    });
    return out;
  };

  const ownIds = [
    ...(ownerId !== "general"
      ? app.attachments.filter((a) => a.parentType === "contact" && a.parentId === ownerId).map((a) => a.id)
      : []),
    ...idsOf(app.entries),
    ...idsOf(app.lists),
    ...idsOf(app.notes),
    ...idsOf(app.reminders),
    ...idsOf(app.banks),
  ];
  const files = app.attachments.filter((a) => ownIds.includes(a.id)).sort((a, b) => b.createdAt - a.createdAt);

  return (
    <div>
      {files.length === 0 ? (
        <div className="rounded-[22px] border border-line bg-surface px-6 py-10 text-center shadow-key">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-[16px] bg-brandsoft text-brand">
            <Paperclip size={20} />
          </div>
          <p className="text-sm font-semibold text-ink">No files yet</p>
          <p className="mt-1 text-xs text-mute">
            Attach receipts to entries, ID scans to contacts, or files to notes and reminders.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-2.5">
          {files.map((a) => (
            <button
              key={a.id}
              type="button"
              onClick={() => setViewing(a)}
              className={cx(
                "flex aspect-square flex-col items-center justify-center overflow-hidden rounded-[18px] border border-line bg-surface shadow-key transition active:scale-95"
              )}
              aria-label={`Open ${a.name}`}
            >
              {a.kind === "image" && a.thumb ? (
                <img src={a.thumb} alt={a.name} className="h-full w-full object-cover" />
              ) : a.kind === "pdf" ? (
                <FileText size={30} className="text-neg" />
              ) : (
                <FileIcon size={26} className="text-mute" />
              )}
            </button>
          ))}
        </div>
      )}
      <AnimatePresence>
        {viewing && <AttachmentViewer meta={viewing} onClose={() => setViewing(null)} />}
      </AnimatePresence>
    </div>
  );
}
