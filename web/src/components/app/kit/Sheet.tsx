"use client";

import { ReactNode, useEffect } from "react";
import { motion } from "framer-motion";
import { ChevronLeft, X } from "lucide-react";
import { cx, IconButton } from "./primitives";

/**
 * Full-screen sheet INSIDE the phone frame — the standard overlay container.
 * Slides up over the app; used by OverlayHost with a rising z-index.
 */
export function Sheet({
  title,
  subtitle,
  onBack,
  onClose,
  actions,
  header,
  footer,
  children,
  zIndex = 40,
  contentClassName,
}: {
  title?: ReactNode;
  subtitle?: ReactNode;
  onBack?: () => void;
  onClose?: () => void;
  actions?: ReactNode;
  header?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  zIndex?: number;
  contentClassName?: string;
}) {
  const close = onBack ?? onClose;
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && close) close();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [close]);

  return (
    <motion.section
      initial={{ y: "100%", opacity: 0.6 }}
      animate={{ y: 0, opacity: 1 }}
      exit={{ y: "100%", opacity: 0.4 }}
      transition={{ type: "spring", damping: 30, stiffness: 300 }}
      className="absolute inset-0 flex flex-col bg-canvas"
      style={{ zIndex }}
      aria-label={typeof title === "string" ? title : "Sheet"}
    >
      {header ?? (
        <header className="flex shrink-0 items-center gap-2 border-b border-line bg-canvas/95 px-3 py-3 backdrop-blur">
          <IconButton
            label="Go back"
            variant="ghost"
            onClick={() => close?.()}
            className="shrink-0"
          >
            {onBack ? <ChevronLeft size={20} /> : <X size={18} />}
          </IconButton>
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-[17px] font-bold tracking-tight text-ink">{title}</h2>
            {subtitle && <p className="truncate text-[11px] text-mute">{subtitle}</p>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-1.5">{actions}</div>}
        </header>
      )}
      <div className={cx("app-scroll flex-1 overflow-y-auto overscroll-contain", contentClassName)}>
        {children}
      </div>
      {footer && (
        <div className="shrink-0 border-t border-line bg-surface/95 px-4 py-3 pb-[max(12px,env(safe-area-inset-bottom))] backdrop-blur">
          {footer}
        </div>
      )}
    </motion.section>
  );
}

/** Partial-height bottom sheet (action menus, pickers). */
export function BottomSheet({
  open,
  onClose,
  title,
  children,
  zIndex = 60,
}: {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  children: ReactNode;
  zIndex?: number;
}) {
  if (!open) return null;
  return (
    <>
      <motion.button
        aria-label="Close sheet"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        onClick={onClose}
        className="absolute inset-0 cursor-default"
        style={{ zIndex: zIndex - 1, background: "var(--app-scrim)" }}
      />
      <motion.div
        initial={{ y: "110%" }}
        animate={{ y: 0 }}
        exit={{ y: "110%" }}
        transition={{ type: "spring", damping: 28, stiffness: 320 }}
        className="sheet-content absolute inset-x-0 bottom-0 flex max-h-[80%] flex-col overflow-hidden rounded-t-[28px] border-t border-line bg-surface shadow-soft"
        style={{ zIndex }}
        role="dialog"
        aria-label={typeof title === "string" ? title : "Bottom sheet"}
      >
        <div className="mx-auto mt-3 h-1 w-10 shrink-0 rounded-full bg-line2" />
        {title && (
          <div className="shrink-0 px-5 pb-1 pt-3">
            <h3 className="text-[15px] font-bold tracking-tight text-ink">{title}</h3>
          </div>
        )}
        <div className="app-scroll flex-1 overflow-y-auto overscroll-contain px-4 pb-[max(16px,env(safe-area-inset-bottom))] pt-2">
          {children}
        </div>
      </motion.div>
    </>
  );
}
