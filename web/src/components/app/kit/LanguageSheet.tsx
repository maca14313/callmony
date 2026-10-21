"use client";

import { Check } from "lucide-react";
import { useApp } from "@/lib/store";
import { LANGUAGES, useT, setLang, type Lang } from "@/lib/i18n";
import { toastOk } from "./toast";
import { BottomSheet } from "./Sheet";
import { cx } from "./primitives";

/**
 * Language picker as a bottom sheet — shared by the More sheet (bottom nav)
 * and Settings → Appearance. One tap switches the whole app, incl. RTL flip.
 */
export function LanguageSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  const t = useT();
  const language = useApp((s) => s.settings.language);

  const pick = (code: Lang) => {
    setLang(code);
    const lang = LANGUAGES.find((l) => l.code === code);
    toastOk(t("st.languageSetTo", { name: lang?.native ?? code }));
    onClose();
  };

  return (
    <BottomSheet open={open} onClose={onClose} title={t("st.language")} zIndex={85}>
      <div className="space-y-1.5 pb-2">
        {LANGUAGES.map((l) => {
          const active = (language ?? "en") === l.code;
          return (
            <button
              key={l.code}
              onClick={() => pick(l.code)}
              className={cx(
                "flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left transition active:scale-[0.98]",
                active ? "border-brand/40 bg-brandsoft" : "border-line bg-surface"
              )}
            >
              <span
                className={cx(
                  "flex h-8 w-8 shrink-0 items-center justify-center rounded-[12px] text-[12px] font-bold",
                  active ? "bg-brand text-white" : "bg-surface2 text-mute"
                )}
              >
                {l.code === "en" ? "A" : l.code === "ar" ? "ع" : "አ"}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[14px] font-semibold text-ink">{l.native}</span>
                <span className="block text-[11px] text-mute">{l.label}</span>
              </span>
              {active && <Check size={16} className="shrink-0 text-brand" />}
            </button>
          );
        })}
      </div>
    </BottomSheet>
  );
}
