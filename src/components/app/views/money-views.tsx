"use client";

import { useMemo, useState } from "react";
import { Inbox, Info } from "lucide-react";
import { useApp } from "@/lib/store";
import { openListsPending, totalsForOwner } from "@/lib/calc";
import { useT } from "@/lib/i18n";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { RemindersSection } from "../sections/RemindersSection";
import { BankSection } from "../sections/BankSection";
import { Money, Segmented } from "../kit/primitives";

type GeneralTab = "ledger" | "lists" | "notes" | "reminders" | "accounts";

/** General — the personal (no-contact) space: income/expense ledger, lists, notes, reminders, accounts. */
export function GeneralView() {
  const t = useT();
  const [tab, setTab] = useState<GeneralTab>("ledger");
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);

  const totals = useMemo(() => totalsForOwner(entries, "general"), [entries]);
  const listsPending = useMemo(() => openListsPending(lists, "general"), [lists]);

  const TABS: { value: GeneralTab; label: string }[] = [
    { value: "ledger", label: t("tab.ledger") },
    { value: "lists", label: t("tab.lists") },
    { value: "notes", label: t("tab.notes") },
    { value: "reminders", label: t("tab.reminders") },
    { value: "accounts", label: "Accounts" },
  ];

  return (
    <div className="tab-content pt-1">
      {/* intro hero */}
      <section
        className="rounded-[22px] border border-line bg-brandsoft p-5 shadow-key"
        aria-label="General overview"
      >
        <div className="flex items-center gap-3">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[17px] bg-brand text-white shadow-floating">
            <Inbox size={22} />
          </span>
          <div className="min-w-0">
            <h2 className="text-[18px] font-bold tracking-tight text-ink">
              {t("hdr.general")}
              <span className="text-brand">.</span>
            </h2>
            <p className="text-[11px] text-mute">Your personal space — income, expenses &amp; lists</p>
          </div>
        </div>

        <dl className="mt-4 grid grid-cols-3 gap-2 text-center">
          <div className="rounded-2xl bg-surface px-2 py-3 shadow-key">
            <dt className="text-[9px] font-bold uppercase tracking-[1.4px] text-mute">{t("lg.income")}</dt>
            <dd className="mt-1 text-[13px] font-bold text-pos">
              <Money value={totals.credit} />
            </dd>
          </div>
          <div className="rounded-2xl bg-surface px-2 py-3 shadow-key">
            <dt className="text-[9px] font-bold uppercase tracking-[1.4px] text-mute">{t("lg.expense")}</dt>
            <dd className="mt-1 text-[13px] font-bold text-neg">
              <Money value={totals.debit} />
            </dd>
          </div>
          <div className="rounded-2xl bg-surface px-2 py-3 shadow-key">
            <dt className="text-[9px] font-bold uppercase tracking-[1.4px] text-mute">{t("lg.net")}</dt>
            <dd className="mt-1 text-[13px] font-bold text-ink">
              <Money value={totals.net} signed />
            </dd>
          </div>
        </dl>

        {listsPending !== 0 && (
          <p className="mt-3 flex items-center justify-center gap-1.5 rounded-2xl bg-amber/10 px-3 py-2 text-[11px] font-semibold text-amber">
            <Info size={12} className="shrink-0" aria-hidden />
            <span>
              {t("lg.openLists")}: +<Money value={listsPending} /> ({t("lg.countedInBalance")})
            </span>
          </p>
        )}
      </section>

      <nav aria-label="General sections">
        <Segmented className="mt-4" options={TABS} value={tab} onChange={setTab} />
      </nav>

      <div className="mt-4 pb-2">
        {tab === "ledger" && <LedgerSection ownerId="general" />}
        {tab === "lists" && <ListsSection ownerId="general" />}
        {tab === "notes" && <NotesSection ownerId="general" />}
        {tab === "reminders" && <RemindersSection ownerId="general" />}
        {tab === "accounts" && <BankSection ownerId="general" />}
      </div>
    </div>
  );
}
