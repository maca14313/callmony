"use client";

import { useMemo, useState } from "react";
import { Inbox, Info, Users } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { openListsPending, ownerFullBalance, r2, totalsForOwner } from "@/lib/calc";
import { useT } from "@/lib/i18n";
import { displayName } from "@/lib/utils";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { RemindersSection } from "../sections/RemindersSection";
import { BankSection } from "../sections/BankSection";
import { Avatar, Money, Segmented } from "../kit/primitives";

type GeneralTab = "ledger" | "lists" | "notes" | "reminders" | "accounts";

/** General — the personal (no-contact) space: income/expense ledger, lists, notes, reminders, accounts. */
export function GeneralView() {
  const t = useT();
  const ui = useUI();
  const [tab, setTab] = useState<GeneralTab>("ledger");
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const contacts = useApp((s) => s.contacts);

  const totals = useMemo(() => totalsForOwner(entries, "general"), [entries]);
  const listsPending = useMemo(() => openListsPending(lists, "general"), [lists]);

  /* people with a live ledger — the General view reflects (and opens) each
     person's ledger: rows appear when the balance ≠ 0 or ≥ 1 live entry,
     sorted by |balance| descending */
  const people = useMemo(
    () =>
      contacts
        .filter((c) => !c.deletedAt)
        .map((c) => {
          const balance = ownerFullBalance(entries, lists, c.id);
          const hasEntries = entries.some((e) => e.ownerId === c.id && !e.deletedAt);
          return { contact: c, balance, hasEntries };
        })
        .filter((p) => Math.abs(p.balance) > 0.005 || p.hasEntries)
        .sort((a, b) => Math.abs(b.balance) - Math.abs(a.balance)),
    [contacts, entries, lists]
  );
  const owedToYou = useMemo(
    () => r2(people.reduce((s, p) => s + Math.max(p.balance, 0), 0)),
    [people]
  );
  const youOwe = useMemo(
    () => r2(people.reduce((s, p) => s + Math.min(p.balance, 0), 0)),
    [people]
  );

  const TABS: { value: GeneralTab; label: string }[] = [
    { value: "ledger", label: t("tab.ledger") },
    { value: "lists", label: t("tab.lists") },
    { value: "notes", label: t("tab.notes") },
    { value: "reminders", label: t("tab.reminders") },
    { value: "accounts", label: t("tab.accounts") },
  ];

  return (
    <div className="tab-content pt-1">
      {/* intro hero */}
      <section
        className="rounded-[22px] border border-line bg-brandsoft p-5 shadow-key"
        aria-label={t("gv.overview")}
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
            <p className="text-[11px] text-mute">{t("gv.intro")}</p>
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

      {/* people — each person's ledger at a glance; tap opens it (ledger-first) */}
      <section className="mt-4 rounded-[22px] border border-line bg-surface p-4 shadow-key" aria-label={t("gv.people")}>
        <div className="flex items-center justify-between gap-2">
          <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[1.7px] text-mute">
            <Users size={13} aria-hidden /> {t("gv.people")}
          </p>
          <div className="flex items-center gap-1.5">
            {owedToYou > 0.005 && (
              <span className="inline-flex items-center rounded-full bg-brandsoft px-2 py-0.5 text-[10px] font-bold text-pos">
                {t("gv.owedToYou")} <Money value={owedToYou} />
              </span>
            )}
            {youOwe < -0.005 && (
              <span className="inline-flex items-center rounded-full bg-negsoft px-2 py-0.5 text-[10px] font-bold text-neg">
                {t("gv.youOwe")} <Money value={youOwe} />
              </span>
            )}
          </div>
        </div>

        {people.length > 0 ? (
          <>
            <p className="mt-1 text-[10.5px] text-mute">{t("gv.peopleHint")}</p>
            <div className="app-scroll mt-2.5 max-h-72 space-y-1.5 overflow-y-auto">
              {people.map(({ contact: c, balance }) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => ui.open({ type: "contact", contactId: c.id })}
                  aria-label={`${displayName(c)} · ${t("lg.balance")}`}
                  className="flex w-full items-center gap-3 rounded-2xl border border-line bg-surface2 px-3 py-2.5 text-left transition hover:bg-brandsoft/40 active:scale-[0.98]"
                >
                  <Avatar contact={c} size="sm" />
                  <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">
                    {displayName(c)}
                  </span>
                  <span className="shrink-0 text-[13px] font-bold">
                    <Money value={balance} signed />
                  </span>
                </button>
              ))}
            </div>
          </>
        ) : (
          <div className="mt-2 rounded-2xl bg-surface2 px-3 py-3 text-center">
            <p className="text-[12px] font-semibold text-ink">{t("gv.noPeople")}</p>
            <p className="mt-1 text-[10.5px] leading-4 text-mute">{t("gv.noPeopleHint")}</p>
          </div>
        )}
      </section>

      <nav aria-label={t("misc.generalSections")}>
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
