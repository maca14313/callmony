"use client";

/* =========================================================================
   Task 2-c — BankSection
   Bank accounts for an owner: masked account number with show/hide,
   per-field copy buttons, share sheet (share as text / copy details),
   attachments strip. Tap → BankEdit · long-press → Edit / Delete.
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
import { copyText, maskAccount, money as fmtMoney } from "@/lib/utils";
import { shareText } from "@/lib/share";
import type { BankAccount, OwnerId } from "@/lib/types";
import { BottomSheet } from "../kit/Sheet";
import { SwipeRow } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { toastOk, toastUndoLast } from "../kit/toast";
import { AttachmentStrip } from "../kit/Attachments";
import { EmptyState, Money, SectionLabel, cx } from "../kit/primitives";

export function BankSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const confirm = useConfirm();
  const banksAll = useApp((s) => s.banks);
  const deleteBank = useApp((s) => s.deleteBank);
  const [sheetFor, setSheetFor] = useState<BankAccount | null>(null);

  const banks = useMemo(
    () =>
      banksAll
        .filter((b) => b.ownerId === ownerId && !b.deletedAt)
        .sort((a, b) => b.updatedAt - a.updatedAt),
    [banksAll, ownerId]
  );

  const remove = async (b: BankAccount) => {
    const ok = await confirm({
      title: "Delete account?",
      message: `“${b.label}” will be moved to Trash — you can undo right after.`,
      confirmLabel: "Delete",
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
      aria-label="Add bank account"
      className="flex h-11 items-center gap-1 rounded-xl bg-brand px-3.5 text-[11px] font-semibold text-white shadow-key transition hover:bg-brandink active:scale-95"
    >
      <Plus size={13} /> Add account
    </button>
  );

  if (!banks.length) {
    return (
      <section aria-label="Bank accounts">
        <SectionLabel right={addBtn}>Bank accounts</SectionLabel>
        <EmptyState
          icon={<Landmark size={21} />}
          title="No accounts saved"
          hint="Keep bank, mobile-money and UPI details here — masked until you reveal them."
        />
      </section>
    );
  }

  return (
    <section aria-label="Bank accounts" className="mt-1">
      <SectionLabel right={addBtn}>Bank accounts · {banks.length}</SectionLabel>

      <div>
        {banks.map((b) => (
          <BankCard key={b.id} bank={b} onLongPress={() => setSheetFor(b)} />
        ))}
      </div>

      {/* long-press actions */}
      <BottomSheet
        open={!!sheetFor}
        onClose={() => setSheetFor(null)}
        title={sheetFor?.label || "Account"}
      >
        <div className="space-y-1.5 pb-2">
          <MenuRow
            icon={<Pencil size={16} />}
            label="Edit account"
            onClick={() => {
              const b = sheetFor;
              setSheetFor(null);
              if (b) ui.open({ type: "bankEdit", bankId: b.id, ownerId });
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

/* ------------------------------------------------------------------ */
/* account card                                                         */
/* ------------------------------------------------------------------ */

function BankCard({ bank: b, onLongPress }: { bank: BankAccount; onLongPress: () => void }) {
  const ui = useUI();
  const currency = useApp((s) => s.settings.currency);
  const [revealed, setRevealed] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);

  const copy = async (value: string | undefined, label: string) => {
    if (!value) return;
    const ok = await copyText(value);
    toastOk(ok ? `${label} copied` : "Copy failed");
  };

  const detailsText = useMemo(() => bankDetailsText(b, currency), [b, currency]);

  const shareAsText = async () => {
    const r = await shareText(b.label, detailsText);
    if (r === "copied") {
      const ok = await copyText(detailsText);
      toastOk(ok ? "Copied — sharing not supported here" : "Copy failed");
    } else if (r === "shared") {
      toastOk("Shared");
    }
    setShareOpen(false);
  };

  const copyDetails = async () => {
    const ok = await copyText(detailsText);
    toastOk(ok ? "Account details copied" : "Copy failed");
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
          {/* header */}
          <div className="flex items-start justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2.5">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-brandsoft text-brand">
                <Landmark size={17} />
              </span>
              <div className="min-w-0">
                <p className="truncate text-[13.5px] font-bold leading-tight text-ink">{b.label}</p>
                {b.bankName && <p className="truncate text-[11px] text-mute">{b.bankName}</p>}
              </div>
            </div>
            <button
              type="button"
              aria-label={`Share ${b.label} details`}
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

          {/* masked account number */}
          <div className="mt-2.5 flex items-center justify-between gap-2 rounded-2xl bg-surface2 px-3.5 py-2.5">
            <p className="min-w-0 truncate text-[13px] font-semibold tracking-[0.8px] tabular-nums text-ink">
              {b.accountNumber
                ? revealed
                  ? b.accountNumber
                  : maskAccount(b.accountNumber)
                : "No account number"}
            </p>
            <span className="flex shrink-0 items-center gap-1">
              {b.accountNumber && (
                <button
                  type="button"
                  aria-label="Copy account number"
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation();
                    void copy(b.accountNumber, "Account number");
                  }}
                  className="flex h-10 w-10 items-center justify-center rounded-full text-mute transition hover:text-brand active:scale-95"
                >
                  <Copy size={14} />
                </button>
              )}
              {b.accountNumber && (
                <button
                  type="button"
                  aria-label={revealed ? "Hide account number" : "Show account number"}
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={(e) => {
                    e.stopPropagation();
                    setRevealed((v) => !v);
                  }}
                  className="flex h-10 w-10 items-center justify-center rounded-full text-mute transition hover:text-brand active:scale-95"
                >
                  {revealed ? <EyeOff size={15} /> : <Eye size={15} />}
                </button>
              )}
            </span>
          </div>

          {/* holder + IBAN/SWIFT/UPI/branch */}
          <div className="mt-2 divide-y divide-line/60">
            <DetailRow label="Holder" value={b.holderName} onCopy={() => void copy(b.holderName, "Holder name")} />
            <DetailRow label="IBAN" value={b.iban} onCopy={() => void copy(b.iban, "IBAN")} />
            <DetailRow label="SWIFT" value={b.swift} onCopy={() => void copy(b.swift, "SWIFT")} />
            <DetailRow label="UPI ID" value={b.upiId} onCopy={() => void copy(b.upiId, "UPI ID")} />
            <DetailRow label="Branch" value={b.branch} onCopy={() => void copy(b.branch, "Branch")} />
          </div>

          {/* balance */}
          {b.balance != null && (
            <div className="mt-2 flex items-center justify-between rounded-2xl bg-brandsoft px-3.5 py-2.5">
              <span className="text-[10px] font-bold uppercase tracking-[1.5px] text-brandink">
                Balance
              </span>
              <Money value={b.balance} className="text-[13px] font-bold" />
            </div>
          )}

          {/* attachments */}
          {b.attachments.length > 0 && (
            <div className="mt-2.5">
              <AttachmentStrip parentType="bank" parentId={b.id} />
            </div>
          )}
        </div>
      </SwipeRow>

      {/* share sheet */}
      <BottomSheet open={shareOpen} onClose={() => setShareOpen(false)} title={`Share “${b.label}”`}>
        <div className="space-y-1.5 pb-2">
          <MenuRow icon={<Share2 size={16} />} label="Share as text" onClick={() => void shareAsText()} />
          <MenuRow icon={<Copy size={16} />} label="Copy details" onClick={() => void copyDetails()} />
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

function DetailRow({
  label,
  value,
  onCopy,
}: {
  label: string;
  value?: string;
  onCopy: () => void;
}) {
  if (!value) return null;
  return (
    <div className="flex items-center justify-between gap-2 py-1.5">
      <span className="shrink-0 text-[9.5px] font-bold uppercase tracking-[1.4px] text-mute">
        {label}
      </span>
      <span className="flex min-w-0 items-center gap-1">
        <span className="truncate text-[12px] font-medium text-ink">{value}</span>
        <button
          type="button"
          aria-label={`Copy ${label}`}
          onPointerDown={(e) => e.stopPropagation()}
          onClick={(e) => {
            e.stopPropagation();
            onCopy();
          }}
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-mute transition hover:text-brand active:scale-90"
        >
          <Copy size={12} />
        </button>
      </span>
    </div>
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

/** WhatsApp-ready plain-text account details. */
export function bankDetailsText(b: BankAccount, fallbackCurrency: string): string {
  const lines: string[] = [];
  lines.push(b.label + (b.bankName ? ` — ${b.bankName}` : ""));
  if (b.holderName) lines.push(`Holder: ${b.holderName}`);
  if (b.accountNumber) lines.push(`Account: ${b.accountNumber}`);
  if (b.iban) lines.push(`IBAN: ${b.iban}`);
  if (b.swift) lines.push(`SWIFT: ${b.swift}`);
  if (b.upiId) lines.push(`UPI: ${b.upiId}`);
  if (b.branch) lines.push(`Branch: ${b.branch}`);
  if (b.balance != null) lines.push(`Balance: ${fmtMoney(b.balance, b.currency || fallbackCurrency)}`);
  return lines.join("\n");
}
