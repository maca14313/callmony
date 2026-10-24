"use client";

/* =========================================================================
   Task 5-c — BankSection (simplified)
   Account cards: bank name, masked account number with reveal toggle,
   copy button, share sheet. The owner (person / General) is implied by
   where the section is rendered. Tap → BankEdit · long-press → Edit/Delete.
   ========================================================================= */

import { useMemo, useState } from "react";
import {
  Copy,
  Eye,
  EyeOff,
  Landmark,
  Pencil,
  Plus,
  Share2,
  Trash2,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { useT } from "@/lib/i18n";
import { copyText, displayName, maskAccount } from "@/lib/utils";
import { shareText } from "@/lib/share";
import type { BankAccount, OwnerId } from "@/lib/types";
import { BottomSheet } from "../kit/Sheet";
import { SwipeRow } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { toastOk, toastUndoLast } from "../kit/toast";
import { EmptyState, IconButton, SectionLabel, cx } from "../kit/primitives";

export function BankSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const t = useT();
  const confirm = useConfirm();
  const banksAll = useApp((s) => s.banks);
  const contactsAll = useApp((s) => s.contacts);
  const deleteBank = useApp((s) => s.deleteBank);
  const [sheetFor, setSheetFor] = useState<BankAccount | null>(null);

  const banks = useMemo(
    () =>
      banksAll
        .filter((b) => b.ownerId === ownerId && !b.deletedAt)
        .sort((a, b) => b.updatedAt - a.updatedAt),
    [banksAll, ownerId]
  );

  /** Owner label for share text — resolved contact name, skipped for General. */
  const holder = useMemo(() => {
    if (ownerId === "general") return undefined;
    const c = contactsAll.find((x) => x.id === ownerId && !x.deletedAt);
    return c ? displayName(c) : undefined;
  }, [contactsAll, ownerId]);

  const remove = async (b: BankAccount) => {
    const ok = await confirm({
      title: t("bk.deleteTitle"),
      message: t("bk.deleteMsg", { name: b.bankName || b.label }),
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    deleteBank(b.id);
    toastUndoLast();
    setSheetFor(null);
  };

  const addBtn = (
    <button
      type="button"
      onClick={() => ui.open({ type: "bankEdit", ownerId })}
      aria-label={t("bk.addAccount")}
      className="flex h-11 items-center gap-1 rounded-xl bg-brand px-3.5 text-[11px] font-semibold text-white shadow-key transition hover:bg-brandink active:scale-95"
    >
      <Plus size={13} /> {t("bk.addAccount")}
    </button>
  );

  if (!banks.length) {
    return (
      <section aria-label={t("bk.accountsTitle")}>
        <SectionLabel right={addBtn}>{t("bk.accountsTitle")}</SectionLabel>
        <EmptyState
          icon={<Landmark size={21} />}
          title={t("bk.noAccounts")}
          hint={t("bk.noAccountsHint")}
        />
      </section>
    );
  }

  return (
    <section aria-label={t("bk.accountsTitle")} className="mt-1">
      <SectionLabel right={addBtn}>
        {t("bk.accountsTitle")} · {banks.length}
      </SectionLabel>

      <div>
        {banks.map((b) => (
          <BankCard key={b.id} bank={b} holder={holder} onLongPress={() => setSheetFor(b)} />
        ))}
      </div>

      {/* long-press actions */}
      <BottomSheet
        open={!!sheetFor}
        onClose={() => setSheetFor(null)}
        title={sheetFor?.bankName || sheetFor?.label || t("tab.accounts")}
      >
        <div className="space-y-1.5 pb-2">
          <MenuRow
            icon={<Pencil size={16} />}
            label={t("act.edit")}
            onClick={() => {
              const b = sheetFor;
              setSheetFor(null);
              if (b) ui.open({ type: "bankEdit", bankId: b.id, ownerId });
            }}
          />
          <MenuRow
            icon={<Trash2 size={16} />}
            label={t("act.delete")}
            danger
            onClick={() => sheetFor && void remove(sheetFor)}
          />
        </div>
      </BottomSheet>
    </section>
  );
}

/* ------------------------------------------------------------------ */
/* account card                                                         */
/* ------------------------------------------------------------------ */

function BankCard({
  bank: b,
  holder,
  onLongPress,
}: {
  bank: BankAccount;
  holder?: string;
  onLongPress: () => void;
}) {
  const t = useT();
  const ui = useUI();
  const [revealed, setRevealed] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);

  const name = b.bankName || b.label;

  /* person owner → contact name; General account → its holder name */
  const effectiveHolder = holder ?? b.holderName;

  const detailsText = useMemo(() => bankDetailsText(b, effectiveHolder), [b, effectiveHolder]);

  const copyNumber = async () => {
    if (!b.accountNumber) return;
    const ok = await copyText(b.accountNumber);
    toastOk(ok ? t("act.copied") : t("act.couldntCopy"));
  };

  const shareAsText = async () => {
    const r = await shareText(name, detailsText);
    if (r === "copied") {
      const ok = await copyText(detailsText);
      toastOk(ok ? "Copied — sharing not supported here" : t("act.couldntCopy"));
    } else if (r === "shared") {
      toastOk(t("bk.shared"));
    }
    setShareOpen(false);
  };

  const copyDetails = async () => {
    const ok = await copyText(detailsText);
    toastOk(ok ? "Account details copied" : t("act.couldntCopy"));
    setShareOpen(false);
  };

  return (
    <>
      <SwipeRow
        onTap={() => ui.open({ type: "bankEdit", bankId: b.id, ownerId: b.ownerId })}
        onLongPress={onLongPress}
        className="mb-2.5"
      >
        <div className="rounded-2xl bg-surface p-3.5 shadow-key">
          {/* header — bank name + share */}
          <div className="flex items-start justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2.5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-brandsoft text-brand">
                <Landmark size={17} />
              </span>
              <div className="min-w-0">
                <p className="truncate text-[13.5px] font-bold leading-tight text-ink">{name}</p>
                {/* person owner → their name; General → holder line when set */}
                {holder ? (
                  <p className="truncate text-[11px] text-mute">{holder}</p>
                ) : b.holderName ? (
                  <p className="truncate text-[11px] font-semibold text-mute">
                    {t("bk.holder")} · {b.holderName}
                  </p>
                ) : null}
              </div>
            </div>
            <button
              type="button"
              aria-label={`${t("act.share")} ${name}`}
              onPointerDown={(e) => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                setShareOpen(true);
              }}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-line2 bg-surface text-mute transition hover:text-brand active:scale-95"
            >
              <Share2 size={15} />
            </button>
          </div>

          {/* masked account number + copy + reveal */}
          <div className="mt-2.5 flex items-center justify-between gap-2 rounded-2xl bg-surface2 px-3.5 py-2.5">
            <p className="min-w-0 truncate text-[13px] font-semibold tracking-[0.8px] tabular-nums text-ink">
              {b.accountNumber
                ? revealed
                  ? b.accountNumber
                  : maskAccount(b.accountNumber)
                : "No account number"}
            </p>
            {/* stop pointerdown here so swipe-row long-press/tap never fire from these buttons */}
            <span
              className="flex shrink-0 items-center gap-1"
              onPointerDown={(e) => e.stopPropagation()}
            >
              {b.accountNumber && (
                <IconButton
                  label={t("act.copy")}
                  variant="surface"
                  onClick={(e) => {
                    e.stopPropagation();
                    void copyNumber();
                  }}
                >
                  <Copy size={14} />
                </IconButton>
              )}
              {b.accountNumber && (
                <IconButton
                  label={revealed ? "Hide account number" : "Show account number"}
                  variant="ghost"
                  onClick={(e) => {
                    e.stopPropagation();
                    setRevealed((v) => !v);
                  }}
                >
                  {revealed ? <EyeOff size={15} /> : <Eye size={15} />}
                </IconButton>
              )}
            </span>
          </div>
        </div>
      </SwipeRow>

      {/* share sheet */}
      <BottomSheet open={shareOpen} onClose={() => setShareOpen(false)} title={`${t("act.share")} “${name}”`}>
        <div className="space-y-1.5 pb-2">
          <MenuRow icon={<Share2 size={16} />} label={t("bk.shareAsText")} onClick={() => void shareAsText()} />
          <MenuRow icon={<Copy size={16} />} label={t("bk.copyDetails")} onClick={() => void copyDetails()} />
        </div>
        <pre className="mt-1 whitespace-pre-wrap rounded-2xl bg-surface2 p-3 text-[11px] leading-5 text-mute">
          {detailsText}
        </pre>
      </BottomSheet>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* bits                                                                 */
/* ------------------------------------------------------------------ */

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

/** Plain-text account details: holder (when known), bank name, account number. */
export function bankDetailsText(b: BankAccount, holder?: string): string {
  const lines: string[] = [];
  if (holder) lines.push(`Holder: ${holder}`);
  lines.push(b.bankName || b.label);
  if (b.accountNumber) lines.push(`Account: ${b.accountNumber}`);
  return lines.join("\n");
}
