"use client";

import { toast } from "sonner";
import { useApp } from "@/lib/store";

/* Toast helpers on top of sonner — themed like the reference UI (dark pill). */

export const toastOk = (message: string) => toast(message);

export const toastErr = (message: string) => toast.error(message, { duration: 3200 });

export const toastUndo = (label: string, onUndo: () => void) =>
  toast(label, {
    duration: 5000,
    action: {
      label: "Undo",
      onClick: onUndo,
    },
  });

/** Undo-aware delete toast wired to the store's single-level undo. */
export const toastUndoLast = () => {
  const { lastUndo, undo } = useApp.getState();
  if (!lastUndo) return;
  toast(lastUndo.label, {
    duration: 5000,
    action: { label: "Undo", onClick: () => undo() },
  });
};
