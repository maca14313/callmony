"use client";

import { createContext, useCallback, useContext, useRef, useState, ReactNode } from "react";
import { AlertTriangle, Check, Trash2 } from "lucide-react";
import { Button } from "./primitives";

/* Custom confirmation modal — used everywhere a destructive/important action happens. */

export interface ConfirmOptions {
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: "danger" | "brand" | "default";
  icon?: ReactNode;
}

type ConfirmFn = (opts: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<ConfirmFn>(async () => false);

export const useConfirm = () => useContext(ConfirmContext);

interface QueueItem extends ConfirmOptions {
  resolve: (v: boolean) => void;
  id: number;
}

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [queue, setQueue] = useState<QueueItem[]>([]);
  const idRef = useRef(0);

  const confirm = useCallback((opts: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      const item: QueueItem = { ...opts, resolve, id: ++idRef.current };
      setQueue((q) => [...q, item]);
    });
  }, []);

  const settle = (id: number, value: boolean) => {
    setQueue((q) => {
      const item = q.find((x) => x.id === id);
      item?.resolve(value);
      return q.filter((x) => x.id !== id);
    });
  };

  const current = queue[0];

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {current && (
        <div
          className="absolute inset-0 flex items-center justify-center p-6"
          style={{ zIndex: 90, background: "var(--app-scrim)" }}
          role="alertdialog"
          aria-modal="true"
          aria-label={current.title}
        >
          <div className="sheet-content w-full max-w-[330px] rounded-[26px] border border-line bg-surface p-5 shadow-soft">
            <div className="flex items-start gap-3.5">
              <div
                className={
                  "flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] " +
                  (current.tone === "danger"
                    ? "bg-negsoft text-neg"
                    : current.tone === "brand"
                      ? "bg-brandsoft text-brand"
                      : "bg-surface2 text-mute")
                }
              >
                {current.icon ??
                  (current.tone === "danger" ? (
                    <Trash2 size={19} />
                  ) : current.tone === "brand" ? (
                    <Check size={19} />
                  ) : (
                    <AlertTriangle size={19} />
                  ))}
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-[15px] font-bold leading-tight text-ink">{current.title}</h3>
                {current.message && (
                  <div className="mt-1.5 text-xs leading-5 text-mute">{current.message}</div>
                )}
              </div>
            </div>
            <div className="mt-5 flex gap-2.5">
              <Button variant="surface" size="lg" className="flex-1" onClick={() => settle(current.id, false)}>
                {current.cancelLabel || "Cancel"}
              </Button>
              <Button
                variant={current.tone === "danger" ? "danger" : "brand"}
                size="lg"
                className="flex-1"
                onClick={() => settle(current.id, true)}
                autoFocus
              >
                {current.confirmLabel || "Confirm"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}
