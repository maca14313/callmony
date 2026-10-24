"use client";

/* =========================================================================
   callmony — Contacts & calls overlays (Task 2-a)
   ContactDetail · ContactEdit · CallScreen · MergeOverlay · SpeedDialOverlay
   ========================================================================= */

import { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDown,
  Ban,
  Bell,
  Building2,
  Cake,
  Camera,
  Check,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  GitMerge,
  Grid3X3,
  Info,
  Landmark,
  Link2,
  ListChecks,
  Mail,
  MapPin,
  MessageCircle,
  Mic,
  MicOff,
  MoreVertical,
  Pencil,
  Phone,
  PhoneCall,
  PhoneOff,
  Pin,
  Plus,
  RefreshCw,
  Send,
  Share2,
  Shield,
  Sparkles,
  Star,
  StickyNote,
  Trash2,
  UserRound,
  UserPlus,
  Volume2,
  VolumeX,
  Wallet,
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { t as tStatic, useT } from "@/lib/i18n";
import { openListsPending, ownerFullBalance } from "@/lib/calc";
import { toVCard } from "@/lib/exporters";
import {
  clockStr,
  copyText,
  displayName,
  downloadBlob,
  fd,
  maskAccount,
  money,
  normalizePhone,
  prettyPhone,
  uid,
  vibrate,
} from "@/lib/utils";
import {
  fillTemplate,
  mailtoLink,
  openExternal,
  openTel,
  primaryEmail,
  primaryPhone,
  reminderTemplates,
  shareText,
  smsLink,
  telegramHandle,
  tgLink,
  waLink,
} from "@/lib/share";
import type {
  BankAccount,
  CallLog,
  Contact,
  ID,
  Phone as PhoneT,
  Settings,
  Social,
} from "@/lib/types";
import { PHONE_LABELS } from "@/lib/types";
import {
  Avatar,
  BalancePill,
  Button,
  EmptyState,
  Field,
  IconButton,
  Money,
  SectionCard,
  SectionLabel,
  Segmented,
  cx,
  inputCls,
} from "../kit/primitives";
import { BottomSheet, Sheet } from "../kit/Sheet";
import { useConfirm } from "../kit/Confirm";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";
import { useContactActions } from "../kit/ActionPicker";
import { ContactPicker } from "../kit/ContactPicker";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { BankSection } from "../sections/BankSection";
import { RemindersSection } from "../sections/RemindersSection";

/* ------------------------------------------------------------------ */
/* Shared bits                                                          */
/* ------------------------------------------------------------------ */

const SPEED_KEYS = ["2", "3", "4", "5", "6", "7", "8", "9"];
const KEY_LETTERS: Record<string, string> = {
  "2": "ABC", "3": "DEF", "4": "GHI", "5": "JKL",
  "6": "MNO", "7": "PQRS", "8": "TUV", "9": "WXYZ",
};
const EMAIL_LABELS = ["home", "work", "other"];
const ADDRESS_LABELS = ["home", "work", "other"];
const SOCIAL_APPS: Social["app"][] = ["telegram", "whatsapp", "signal", "instagram", "x", "other"];

type SettingsPatch = Partial<Settings> & { speedDial?: Record<string, string | undefined> };

function speedDialMap(settings: Settings): Record<string, string | undefined> {
  return (settings as Settings & { speedDial?: Record<string, string | undefined> }).speedDial ?? {};
}

async function shareVCard(c: Contact) {
  const vcf = toVCard([c], true);
  const res = await shareText(`Contact · ${displayName(c)}`, vcf);
  if (res === "copied") {
    await copyText(vcf);
    toastOk(tStatic("cd.vcardCopied"));
  } else if (res === "failed") {
    downloadBlob(new Blob([vcf], { type: "text/vcard" }), `${displayName(c)}.vcf`);
    toastOk(tStatic("cd.vcardDownloaded"));
  }
}

function daysUntilBirthday(bd?: string): number | null {
  if (!bd) return null;
  const d = new Date(`${bd}T00:00:00`);
  if (isNaN(d.getTime())) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  let next = new Date(today.getFullYear(), d.getMonth(), d.getDate());
  if (next.getTime() < today.getTime()) next = new Date(today.getFullYear() + 1, d.getMonth(), d.getDate());
  return Math.round((next.getTime() - today.getTime()) / 86400000);
}

function ActionRow({
  icon,
  title,
  sub,
  onClick,
  danger,
}: {
  icon: React.ReactNode;
  title: string;
  sub?: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition active:scale-[0.98]",
        danger ? "text-neg" : "text-ink",
        "hover:bg-surface2"
      )}
    >
      <span
        className={cx(
          "flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px]",
          danger ? "bg-negsoft text-neg" : "bg-brandsoft text-brand"
        )}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-semibold">{title}</span>
        {sub && <span className="block truncate text-[11px] text-mute">{sub}</span>}
      </span>
    </button>
  );
}

function LabelChip({ children }: { children: React.ReactNode }) {
  return (
    <span className="shrink-0 rounded-full bg-brandsoft px-2 py-0.5 text-[9px] font-bold uppercase tracking-[1px] text-brand">
      {children}
    </span>
  );
}

/** Accessible styled toggle (shared shadcn switch is currently broken — see worklog). */
function SwitchToggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className="flex h-11 w-16 shrink-0 items-center justify-center rounded-full transition active:scale-95"
    >
      <span
        aria-hidden
        className={cx("relative h-7 w-12 rounded-full transition-colors", checked ? "bg-brand" : "bg-line2")}
      >
        <span
          className={cx(
            "absolute left-0.5 top-0.5 h-6 w-6 rounded-full bg-white shadow-key transition-transform",
            checked ? "translate-x-[20px]" : "translate-x-0"
          )}
        />
      </span>
    </button>
  );
}

const SOCIAL_ICONS: Record<Social["app"], React.ReactNode> = {
  telegram: <Send size={15} />,
  whatsapp: <MessageCircle size={15} />,
  signal: <Shield size={15} />,
  instagram: <Camera size={15} />,
  x: <Sparkles size={15} />,
  other: <Link2 size={15} />,
};

function socialUrl(s: Social): string | null {
  const h = s.handle.trim().replace(/^@/, "");
  switch (s.app) {
    case "telegram":
      return tgLink(s.handle);
    case "whatsapp":
      return waLink(h);
    case "instagram":
      return `https://instagram.com/${encodeURIComponent(h)}`;
    case "x":
      return `https://x.com/${encodeURIComponent(h)}`;
    case "signal":
      return `https://signal.me/#p/${encodeURIComponent(h)}`;
    default:
      return /^https?:\/\//i.test(h) ? h : null;
  }
}

/* ------------------------------------------------------------------ */
/* ContactDetail — Ledger-first, 6-action grid                          */
/* ------------------------------------------------------------------ */

type DetailTab = "ledger" | "lists" | "notes" | "bank" | "reminders" | "info";

export function ContactDetail({ contactId }: { contactId: ID }) {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const t = useT();
  const { act } = useContactActions();
  const [tab, setTab] = useState<DetailTab>("ledger");
  const [menuOpen, setMenuOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const [phoneSheet, setPhoneSheet] = useState<PhoneT | null>(null);
  const [bankSheetOpen, setBankSheetOpen] = useState(false);

  const contact = app.contacts.find((c) => c.id === contactId && !c.deletedAt);
  const cc = app.settings.countryCode;

  const balance = useMemo(
    () => (contact ? ownerFullBalance(app.entries, app.lists, contact.id) : 0),
    [app.entries, app.lists, contact]
  );
  const openPending = useMemo(
    () => (contact ? openListsPending(app.lists, contact.id) : 0),
    [app.lists, contact]
  );

  /* this contact's bank accounts — for the quick-grid Bank modal */
  const banks = useMemo(
    () =>
      contact
        ? app.banks
            .filter((b) => b.ownerId === contact.id && !b.deletedAt)
            .sort((a, b) => b.updatedAt - a.updatedAt)
        : [],
    [app.banks, contact]
  );

  if (!contact) {
    return (
      <Sheet title={t("cd.contact")} onBack={ui.pop}>
        <div className="p-5">
          <EmptyState
            icon={<UserRound size={22} />}
            title={t("cd.notFound")}
            hint={t("cd.notFoundHint")}
          />
        </div>
      </Sheet>
    );
  }

  const settled = Math.abs(balance) < 0.005;
  const balanceLabel = settled ? t("cd.settledUp") : balance > 0 ? t("cd.theyOweYou") : t("cd.youOweThem");
  const phone = primaryPhone(contact);
  const hasPhones = contact.phones.length > 0;
  const email = primaryEmail(contact);
  const tg = telegramHandle(contact);
  const hasTelegramSocial = contact.socials.some((s) => s.app === "telegram");
  const name = displayName(contact);
  const subtitle = contact.company || (phone ? prettyPhone(phone, cc) : undefined);

  /* WhatsApp payment reminder — one tap opens WhatsApp pre-filled. */
  const waReminder = settled
    ? undefined
    : balance > 0
      ? fillTemplate(reminderTemplates[0].body, {
          name,
          amount: money(Math.abs(balance), app.settings.currency),
          date: fd(new Date()),
        })
      : `Hi ${name}, I'd like to settle up ${money(Math.abs(balance), app.settings.currency)} that I owe you. How would you like to receive it?`;

  const remove = async () => {
    const ok = await confirm({
      title: t("cd.deleteTitle"),
      message: t("cd.deleteMsg", { name }),
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    app.deleteContact(contact.id);
    toastUndoLast();
    ui.pop();
  };

  const setPrimaryPhone = (p: PhoneT) => {
    app.saveContact({ id: contact.id, phones: contact.phones.map((x) => ({ ...x, primary: x.id === p.id })) });
    setPhoneSheet(null);
    toastOk(t("cd.primaryUpdated"));
  };

  const birthdayIn = daysUntilBirthday(contact.birthday);

  return (
    <Sheet
      title={name}
      subtitle={subtitle}
      onBack={ui.pop}
      actions={
        <>
          <IconButton
            label={contact.favorite ? t("cd.removeFavorite") : t("cd.addFavorite")}
            variant={contact.favorite ? "brand" : "ghost"}
            onClick={() => {
              app.toggleFavorite(contact.id);
              toastOk(contact.favorite ? t("cd.removedFromFavorites") : t("cd.addToFavorites"));
            }}
          >
            <Star size={17} fill={contact.favorite ? "currentColor" : "none"} />
          </IconButton>
          <IconButton label={t("cd.moreActions")} onClick={() => setMenuOpen(true)}>
            <MoreVertical size={18} />
          </IconButton>
        </>
      }
    >
      <div className="px-5 pb-10 pt-4">
        {/* hero */}
        <div className="flex flex-col items-center">
          <Avatar contact={contact} size="xl" />
          <div className="mt-3 flex items-center justify-center gap-1.5">
            <h2 className="text-[22px] font-bold tracking-tight text-ink">{name}</h2>
            <IconButton
              label={t("cd.editContact")}
              variant="surface"
              className="h-9 w-9"
              onClick={() => ui.open({ type: "contactEdit", contactId: contact.id })}
            >
              <Pencil size={14} />
            </IconButton>
          </div>
          {contact.company && (
            <p className="mt-0.5 text-[11px] text-mute">
              {contact.jobTitle ? `${contact.jobTitle} · ` : ""}
              {contact.company}
            </p>
          )}
          <div className="mt-2.5">
            <BalancePill value={balance} size="lg" />
          </div>
          <p className="mt-1.5 text-[11px] font-medium text-mute">{balanceLabel}</p>
          {openPending !== 0 && (
            <p className="mt-1 text-[10px] text-mute">
              {t("lg.openLists")} <Money value={openPending} signed /> {t("lg.countedInBalance")}
            </p>
          )}
        </div>

        {/* quick actions — fixed 6×2 icon grid (12 compact tiles) */}
        <div className="mt-5 grid grid-cols-6 gap-1">
          {/* row 1 */}
          <GridTile
            icon={<PhoneCall size={17} className="text-brand" />}
            label={t("act.call")}
            disabled={!hasPhones}
            onClick={() => act(contact, "call")}
          />
          <GridTile
            icon={<MessageCircle size={17} className="text-ink" />}
            label={t("act.sms")}
            disabled={!hasPhones}
            onClick={() => act(contact, "sms")}
          />
          <GridTile
            icon={<MessageCircle size={17} className="text-[#1faa53]" />}
            label={t("act.whatsapp")}
            disabled={!hasPhones}
            onClick={() => act(contact, "whatsapp", { text: waReminder })}
          />
          <GridTile
            icon={<Send size={17} className="text-ink" />}
            label={t("act.telegram")}
            disabled={!tg}
            onClick={() => act(contact, "telegram")}
          />
          <GridTile
            icon={<Mail size={17} className="text-ink" />}
            label={t("act.email")}
            disabled={!email}
            onClick={() => act(contact, "email")}
          />
          <GridTile
            icon={<Wallet size={17} className="text-ink" />}
            label={t("tab.ledger")}
            onClick={() => setTab("ledger")}
          />
          {/* row 2 */}
          <GridTile
            icon={<Landmark size={17} className="text-ink" />}
            label={t("tab.bank")}
            onClick={() => setBankSheetOpen(true)}
          />
          <GridTile
            icon={<ListChecks size={17} className="text-ink" />}
            label={t("tab.lists")}
            onClick={() => setTab("lists")}
          />
          <GridTile
            icon={<StickyNote size={17} className="text-ink" />}
            label={t("tab.notes")}
            onClick={() => setTab("notes")}
          />
          <GridTile
            icon={<Bell size={17} className="text-ink" />}
            label={t("tab.reminders")}
            onClick={() => setTab("reminders")}
          />
          <GridTile
            icon={<Info size={17} className="text-ink" />}
            label={t("tab.info")}
            onClick={() => setTab("info")}
          />
          <GridTile
            icon={<MoreVertical size={17} className="text-ink" />}
            label={t("act.more")}
            onClick={() => setMoreOpen(true)}
          />
        </div>

        {/* tabs */}
        <div className="mt-5">
          <Segmented
            value={tab}
            onChange={setTab}
            options={[
              { value: "ledger", label: t("tab.ledger") },
              { value: "lists", label: t("tab.lists") },
              { value: "notes", label: t("tab.notes") },
              { value: "bank", label: t("tab.bank") },
              { value: "reminders", label: t("tab.reminders") },
              { value: "info", label: t("tab.info") },
            ]}
          />

          <div className="mt-4 space-y-4">
            {tab === "ledger" && <LedgerSection ownerId={contact.id} />}
            {tab === "lists" && <ListsSection ownerId={contact.id} />}
            {tab === "notes" && <NotesSection ownerId={contact.id} />}
            {tab === "bank" && <BankSection ownerId={contact.id} />}
            {tab === "reminders" && <RemindersSection ownerId={contact.id} />}
            {tab === "reminders" && (
              <p className="text-center text-[10px] text-mute">
                {t("cd.remindersFor", { name })}
              </p>
            )}

            {tab === "info" && (
              <motion.div
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className="space-y-4"
              >
                {/* phones */}
                {contact.phones.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.phones")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.phones.map((p) => (
                        <div key={p.id} className="flex items-center gap-1 pr-3">
                          <button
                            type="button"
                            onClick={() => setPhoneSheet(p)}
                            className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                            aria-label={t("ce.numberOptions", { number: prettyPhone(p.number, cc) })}
                          >
                            <LabelChip>{p.label}</LabelChip>
                            <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">
                              {prettyPhone(p.number, cc)}
                            </span>
                            <Star
                              size={12}
                              className={cx("shrink-0", p.primary ? "text-brand" : "text-line2")}
                              fill={p.primary ? "currentColor" : "none"}
                            />
                            <ChevronRight size={14} className="shrink-0 text-line2" />
                          </button>
                          <IconButton
                            label={`${t("act.copy")} ${prettyPhone(p.number, cc)}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(p.number).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* emails */}
                {contact.emails.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.emails")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.emails.map((e) => (
                        <div key={e.id} className="flex items-center gap-2 px-4 py-3">
                          <LabelChip>{e.label}</LabelChip>
                          <button
                            type="button"
                            className="min-w-0 flex-1 truncate text-left text-[13px] font-semibold text-ink"
                            onClick={() => {
                              void copyText(e.address).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")));
                            }}
                            aria-label={`${t("act.copy")} ${e.address}`}
                          >
                            {e.address}
                          </button>
                          <IconButton
                            label={`${t("act.copy")} ${e.address}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(e.address).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                          <IconButton
                            label={`Write email to ${e.address}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() => openExternal(mailtoLink(e.address))}
                          >
                            <Mail size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* socials */}
                {contact.socials.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.socials")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.socials.map((s) => (
                        <div key={s.id} className="flex items-center gap-1 pr-3">
                          <button
                            type="button"
                            onClick={() => {
                              const url = socialUrl(s);
                              if (url) openExternal(url);
                              else {
                                void copyText(s.handle).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")));
                              }
                            }}
                            className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                            aria-label={`${t("act.open")} ${s.app}`}
                          >
                            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                              {SOCIAL_ICONS[s.app]}
                            </span>
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-[13px] font-semibold text-ink">
                                {s.handle}
                              </span>
                              <span className="block text-[10px] uppercase tracking-[1.5px] text-mute">{s.app}</span>
                            </span>
                            <ChevronRight size={14} className="shrink-0 text-line2" />
                          </button>
                          <IconButton
                            label={`${t("act.copy")} ${s.app}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(s.handle).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* links */}
                {contact.links.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.links")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.links.map((l) => (
                        <div key={l.id} className="flex items-center gap-1 pr-3">
                          <button
                            type="button"
                            onClick={() => openExternal(l.url)}
                            className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                            aria-label={`${t("act.open")} ${l.label || l.url}`}
                          >
                            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                              <Link2 size={15} />
                            </span>
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-[13px] font-semibold text-ink">{l.label}</span>
                              <span className="block truncate text-[10px] text-mute">{l.url}</span>
                            </span>
                            <ChevronRight size={14} className="shrink-0 text-line2" />
                          </button>
                          <IconButton
                            label={`${t("act.copy")} ${l.label || l.url}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(l.url).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* addresses */}
                {contact.addresses.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.addresses")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.addresses.map((a) => (
                        <div key={a.id} className="flex items-center gap-1 pr-3">
                          <button
                            type="button"
                            onClick={() => {
                              void copyText(a.text).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")));
                            }}
                            className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                            aria-label={`${t("act.copy")} ${a.text}`}
                          >
                            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                              <MapPin size={15} />
                            </span>
                            <span className="min-w-0 flex-1">
                              <span className="block truncate text-[13px] font-semibold text-ink">{a.text}</span>
                              <span className="block text-[10px] uppercase tracking-[1.5px] text-mute">{a.label}</span>
                            </span>
                          </button>
                          <IconButton
                            label={`${t("act.copy")} ${a.text}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(a.text).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* birthday */}
                {contact.birthday && (
                  <section>
                    <SectionLabel>{t("cd.birthday")}</SectionLabel>
                    <SectionCard className="flex items-center gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-brandsoft text-brand">
                        <Cake size={17} />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-[13px] font-semibold text-ink">{fd(contact.birthday)}</p>
                        {birthdayIn !== null && (
                          <p className="text-[11px] text-mute">
                            {birthdayIn === 0
                              ? t("cd.birthdayToday")
                              : birthdayIn === 1
                                ? t("cd.birthdayTomorrow")
                                : t("cd.birthdayIn", { n: birthdayIn })}
                          </p>
                        )}
                      </div>
                    </SectionCard>
                  </section>
                )}

                {/* company */}
                {(contact.company || contact.jobTitle) && (
                  <section>
                    <SectionLabel>Work</SectionLabel>
                    <SectionCard className="flex items-center gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-brandsoft text-brand">
                        <Building2 size={17} />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-[13px] font-semibold text-ink">{contact.company || "—"}</p>
                        {contact.jobTitle && <p className="text-[11px] text-mute">{contact.jobTitle}</p>}
                      </div>
                    </SectionCard>
                  </section>
                )}

                {/* custom fields */}
                {contact.customFields.length > 0 && (
                  <section>
                    <SectionLabel>{t("cd.customFields")}</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.customFields.map((f) => (
                        <div key={f.id} className="flex items-center gap-1 pr-3">
                          <div className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3">
                            <span className="w-[92px] shrink-0 truncate text-[10px] font-bold uppercase tracking-[1.2px] text-mute">
                              {f.key}
                            </span>
                            <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">{f.value}</span>
                          </div>
                          <IconButton
                            label={`${t("act.copy")} ${f.key}`}
                            variant="surface"
                            className="h-9 w-9"
                            onClick={() =>
                              void copyText(f.value).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
                            }
                          >
                            <Copy size={14} />
                          </IconButton>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* tags */}
                {contact.tags.length > 0 && (
                  <section>
                    <SectionLabel>{t("ce.tags")}</SectionLabel>
                    <div className="flex flex-wrap gap-1.5">
                      {contact.tags.map((t) => (
                        <span
                          key={t}
                          className="rounded-full border border-line bg-surface px-3 py-1.5 text-[11px] font-semibold text-ink shadow-key"
                        >
                          {t}
                        </span>
                      ))}
                    </div>
                  </section>
                )}

                {/* notes */}
                {(contact.pinnedNote || contact.note) && (
                  <section>
                    <SectionLabel>{t("cd.notesSection")}</SectionLabel>
                    <div className="space-y-2.5">
                      {contact.pinnedNote && (
                        <div className="flex gap-3 rounded-[22px] border border-brand/25 bg-brandsoft p-4">
                          <Pin size={15} className="mt-0.5 shrink-0 text-brand" />
                          <p className="whitespace-pre-wrap text-[12px] font-medium leading-5 text-ink">
                            {contact.pinnedNote}
                          </p>
                        </div>
                      )}
                      {contact.note && (
                        <div className="flex gap-3 rounded-[22px] border border-line bg-surface p-4 shadow-key">
                          <StickyNote size={15} className="mt-0.5 shrink-0 text-mute" />
                          <p className="whitespace-pre-wrap text-[12px] leading-5 text-ink">{contact.note}</p>
                        </div>
                      )}
                    </div>
                  </section>
                )}
              </motion.div>
            )}
          </div>
        </div>
      </div>

      {/* header ⋯ menu */}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title={name}>
        <div className="space-y-0.5 pb-2">
          <ActionRow
            icon={<Pencil size={17} />}
            title={t("cd.editContact")}
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "contactEdit", contactId: contact.id });
            }}
          />
          <ActionRow
            icon={<Share2 size={17} />}
            title={t("cd.shareVcard")}
            onClick={() => {
              setMenuOpen(false);
              void shareVCard(contact);
            }}
          />
          <ActionRow
            icon={<GitMerge size={17} />}
            title={t("spd.mergeDuplicates")}
            sub={t("spd.scanSimilar")}
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "merge" });
            }}
          />
          <ActionRow
            icon={<Trash2 size={17} />}
            title="Delete contact"
            danger
            onClick={() => {
              setMenuOpen(false);
              void remove();
            }}
          />
        </div>
      </BottomSheet>

      {/* quick-action "More" sheet */}
      <BottomSheet open={moreOpen} onClose={() => setMoreOpen(false)} title={t("act.more")}>
        <div className="space-y-0.5 pb-2">
          <ActionRow
            icon={<Share2 size={17} />}
            title={t("cd.shareVcard")}
            onClick={() => {
              setMoreOpen(false);
              void shareVCard(contact);
            }}
          />
          <ActionRow
            icon={<Mail size={17} />}
            title={t("act.email")}
            sub={email}
            onClick={() => {
              setMoreOpen(false);
              act(contact, "email");
            }}
          />
          {phone && (
            <ActionRow
              icon={<Copy size={17} />}
              title={t("cd.copyNumber")}
              sub={prettyPhone(phone, cc)}
              onClick={() => {
                void copyText(phone).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")));
                setMoreOpen(false);
              }}
            />
          )}
          {phone && (
            <ActionRow
              icon={<Phone size={17} />}
              title={t("cd.openInDialer")}
              onClick={() => {
                setMoreOpen(false);
                openTel(phone, cc);
              }}
            />
          )}
          {phone && (
            <ActionRow
              icon={<PhoneCall size={17} />}
              title={t("cd.simulateIncoming")}
              onClick={() => {
                setMoreOpen(false);
                ui.open({ type: "call", phone, contactId: contact.id, direction: "incoming" });
              }}
            />
          )}
        </div>
      </BottomSheet>

      {/* quick-grid Bank modal — this contact's accounts */}
      <BottomSheet
        open={bankSheetOpen}
        onClose={() => setBankSheetOpen(false)}
        title={t("bk.accountsOf", { name })}
      >
        {banks.length === 0 ? (
          <div className="px-5 pb-2">
            <EmptyState
              icon={<Landmark size={21} />}
              title={t("bk.noAccounts")}
              hint={t("bk.noAccountsHint")}
            />
          </div>
        ) : (
          <div className="space-y-1.5 pb-1">
            {banks.map((b) => (
              <BankListRow
                key={b.id}
                bank={b}
                onOpen={() => {
                  setBankSheetOpen(false);
                  ui.open({ type: "bankEdit", bankId: b.id, ownerId: contact.id });
                }}
              />
            ))}
          </div>
        )}
        <div className="pb-[max(14px,env(safe-area-inset-bottom))] pt-2">
          <button
            type="button"
            onClick={() => {
              setBankSheetOpen(false);
              ui.open({ type: "bankEdit", ownerId: contact.id });
            }}
            aria-label={t("bk.addAccount")}
            className="flex h-11 w-full items-center justify-center gap-1.5 rounded-2xl border border-dashed border-line2 bg-surface2/60 text-[12px] font-semibold text-brand transition active:scale-[0.98]"
          >
            <Plus size={15} /> {t("bk.addAccount")}
          </button>
        </div>
      </BottomSheet>

      {/* per-phone action sheet */}
      <BottomSheet
        open={!!phoneSheet}
        onClose={() => setPhoneSheet(null)}
        title={phoneSheet ? prettyPhone(phoneSheet.number, cc) : ""}
      >
        {phoneSheet && (
          <div className="space-y-0.5 pb-2">
            <ActionRow
              icon={<Phone size={17} />}
              title={t("act.call")}
              onClick={() => {
                const p = phoneSheet.number;
                setPhoneSheet(null);
                ui.open({ type: "call", phone: p, contactId: contact.id });
              }}
            />
            <ActionRow
              icon={<MessageCircle size={17} />}
              title={t("act.sms")}
              onClick={() => {
                openExternal(smsLink(phoneSheet.number, undefined, cc));
                setPhoneSheet(null);
              }}
            />
            <ActionRow
              icon={<MessageCircle size={17} />}
              title={t("act.whatsapp")}
              onClick={() => {
                openExternal(waLink(phoneSheet.number, undefined, cc));
                setPhoneSheet(null);
              }}
            />
            {hasTelegramSocial && tg && (
              <ActionRow
                icon={<Send size={17} />}
                title={t("act.telegram")}
                sub={`@${tg.replace(/^@/, "")}`}
                onClick={() => {
                  openExternal(tgLink(tg));
                  setPhoneSheet(null);
                }}
              />
            )}
            <ActionRow
              icon={<Copy size={17} />}
              title={t("cd.copyNumber")}
              onClick={() => {
                void copyText(phoneSheet.number).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")));
                setPhoneSheet(null);
              }}
            />
            {!phoneSheet.primary && (
              <ActionRow
                icon={<Star size={17} />}
                title={t("ce.setPrimary")}
                onClick={() => setPrimaryPhone(phoneSheet)}
              />
            )}
          </div>
        )}
      </BottomSheet>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* quick-grid tile — plain 17px icon + 8px label, no card chrome       */
/* ------------------------------------------------------------------ */

function GridTile({
  icon,
  label,
  onClick,
  disabled,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      aria-disabled={disabled}
      className={cx(
        "flex min-h-[44px] w-full flex-col items-center justify-center gap-1 rounded-xl px-0.5 py-1.5 transition active:scale-95 hover:bg-surface2",
        disabled && "pointer-events-none opacity-40"
      )}
    >
      {icon}
      <span className="max-w-full truncate text-[8px] font-semibold text-mute">{label}</span>
    </button>
  );
}

/* ------------------------------------------------------------------ */
/* bank row inside the quick-grid Bank modal                           */
/* ------------------------------------------------------------------ */

function BankListRow({ bank: b, onOpen }: { bank: BankAccount; onOpen: () => void }) {
  const t = useT();
  const [revealed, setRevealed] = useState(false);
  const name = b.bankName || b.label;

  return (
    <div className="flex items-center gap-1.5 rounded-2xl border border-line bg-surface px-3.5 py-2.5">
      <button
        type="button"
        onClick={onOpen}
        className="flex min-w-0 flex-1 items-center gap-2.5 text-left"
        aria-label={name}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
          <Landmark size={15} />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[13px] font-semibold text-ink">{name}</span>
          <span className="block truncate text-[11px] font-medium tabular-nums tracking-[0.6px] text-mute">
            {b.accountNumber ? (revealed ? b.accountNumber : maskAccount(b.accountNumber)) : "—"}
          </span>
        </span>
      </button>
      {b.accountNumber && (
        <>
          <IconButton
            label={revealed ? "Hide account number" : "Show account number"}
            variant="ghost"
            className="h-9 w-9"
            onClick={() => setRevealed((v) => !v)}
          >
            {revealed ? <EyeOff size={15} /> : <Eye size={15} />}
          </IconButton>
          <IconButton
            label={`${t("act.copy")} ${name}`}
            variant="ghost"
            className="h-9 w-9"
            onClick={() =>
              void copyText(b.accountNumber!).then((ok) => toastOk(ok ? t("act.copied") : t("act.couldntCopy")))
            }
          >
            <Copy size={15} />
          </IconButton>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* ContactEdit                                                          */
/* ------------------------------------------------------------------ */

type PhoneDraft = { id: string; label: string; number: string; primary: boolean };
type EmailDraft = { id: string; label: string; address: string; primary: boolean };
type SocialDraft = { id: string; app: Social["app"]; handle: string };
type LinkDraft = { id: string; label: string; url: string };
type AddressDraft = { id: string; label: string; text: string };
type CustomDraft = { id: string; key: string; value: string };

const removeBtnCls =
  "flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-line2 bg-surface text-mute shadow-key transition active:scale-90 hover:text-neg";
const addBtnCls =
  "flex h-11 w-full items-center justify-center gap-1.5 rounded-2xl border border-dashed border-line2 bg-surface2/60 text-[12px] font-semibold text-brand transition active:scale-[0.98]";

export function ContactEdit({
  contactId,
  prefillPhone,
  prefillName,
}: {
  contactId?: ID;
  prefillPhone?: string;
  prefillName?: string;
}) {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const t = useT();
  const existing = contactId ? app.contacts.find((c) => c.id === contactId && !c.deletedAt) : undefined;

  const existingThumb = existing?.photoId
    ? app.attachments.find((a) => a.id === existing.photoId)?.thumb
    : undefined;

  const [firstName, setFirstName] = useState(existing?.firstName ?? prefillName?.split(/\s+/)[0] ?? "");
  const [lastName, setLastName] = useState(existing?.lastName ?? prefillName?.split(/\s+/).slice(1).join(" ") ?? "");
  const [nickname, setNickname] = useState(existing?.nickname ?? "");
  const [company, setCompany] = useState(existing?.company ?? "");
  const [jobTitle, setJobTitle] = useState(existing?.jobTitle ?? "");
  const [phones, setPhones] = useState<PhoneDraft[]>(() =>
    existing?.phones?.length
      ? existing.phones.map((p) => ({ id: p.id, label: p.label, number: p.number, primary: !!p.primary }))
      : prefillPhone
        ? [{ id: uid(), label: "mobile", number: prefillPhone, primary: true }]
        : []
  );
  const [emails, setEmails] = useState<EmailDraft[]>(
    () => existing?.emails?.map((e) => ({ id: e.id, label: e.label, address: e.address, primary: !!e.primary })) ?? []
  );
  const [socials, setSocials] = useState<SocialDraft[]>(() => existing?.socials?.map((s) => ({ ...s })) ?? []);
  const [links, setLinks] = useState<LinkDraft[]>(() => existing?.links?.map((l) => ({ ...l })) ?? []);
  const [addresses, setAddresses] = useState<AddressDraft[]>(
    () => existing?.addresses?.map((a) => ({ ...a })) ?? []
  );
  const [customFields, setCustomFields] = useState<CustomDraft[]>(
    () => existing?.customFields?.map((f) => ({ ...f })) ?? []
  );
  const [birthday, setBirthday] = useState(existing?.birthday ?? "");
  const [tagsStr, setTagsStr] = useState((existing?.tags ?? []).join(", "));
  const [favorite, setFavorite] = useState(existing?.favorite ?? false);
  const [note, setNote] = useState(existing?.note ?? "");
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(existingThumb ?? null);
  const [saving, setSaving] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  if (contactId && !existing) {
    return (
      <Sheet title={t("ce.editContact")} onBack={ui.pop}>
        <div className="p-5">
          <EmptyState
            icon={<UserRound size={22} />}
            title={t("cd.notFound")}
            hint="It may have been deleted. Try again from the contacts list."
          />
        </div>
      </Sheet>
    );
  }

  const save = async () => {
    const hasName = !!(firstName.trim() || lastName.trim() || nickname.trim());
    const filledPhones = phones.filter((p) => p.number.trim());
    if (!hasName && filledPhones.length === 0) {
      toastErr(t("ce.atLeastOne"));
      return;
    }
    setSaving(true);

    const hasPhonePrimary = filledPhones.some((p) => p.primary);
    const filledEmails = emails.filter((e) => e.address.trim());
    const hasEmailPrimary = filledEmails.some((e) => e.primary);

    const saved = app.saveContact({
      ...(contactId ? { id: contactId } : {}),
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      nickname: nickname.trim() || undefined,
      company: company.trim() || undefined,
      jobTitle: jobTitle.trim() || undefined,
      phones: filledPhones.map((p, i) => ({
        id: p.id,
        label: p.label,
        number: p.number.trim(),
        primary: hasPhonePrimary ? !!p.primary : i === 0,
      })),
      emails: filledEmails.map((e, i) => ({
        id: e.id,
        label: e.label,
        address: e.address.trim(),
        primary: hasEmailPrimary ? !!e.primary : i === 0,
      })),
      socials: socials
        .filter((s) => s.handle.trim())
        .map((s) => ({ id: s.id, app: s.app, handle: s.handle.trim().replace(/^@/, "") })),
      links: links
        .filter((l) => l.url.trim())
        .map((l) => ({ id: l.id, label: l.label.trim() || "website", url: l.url.trim() })),
      addresses: addresses
        .filter((a) => a.text.trim())
        .map((a) => ({ id: a.id, label: a.label, text: a.text.trim() })),
      customFields: customFields
        .filter((f) => f.key.trim() && f.value.trim())
        .map((f) => ({ id: f.id, key: f.key.trim(), value: f.value.trim() })),
      birthday: birthday || undefined,
      tags: tagsStr
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean),
      favorite,
      note: note.trim() || undefined,
    });

    if (photoFile) {
      const meta = await app.addAttachment(photoFile, "contact", saved.id);
      if (meta) app.saveContact({ id: saved.id, photoId: meta.id });
    }

    setSaving(false);
    toastOk(contactId ? "Contact updated" : "Contact saved");
    ui.pop();
  };

  const remove = async () => {
    if (!existing) return;
    const ok = await confirm({
      title: t("cd.deleteTitle"),
      message: `“${displayName(existing)}” will move to Trash. You can undo right after.`,
      confirmLabel: t("act.delete"),
      tone: "danger",
    });
    if (!ok) return;
    app.deleteContact(existing.id);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={contactId ? t("ce.editContact") : t("ce.newContact")}
      subtitle={existing ? displayName(existing) : prefillPhone ? prettyPhone(prefillPhone) : undefined}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          {contactId && (
            <Button variant="surface" size="lg" className="flex-1 text-neg" onClick={() => void remove()}>
              <Trash2 size={16} /> {t("act.delete")}
            </Button>
          )}
          <Button variant="brand" size="lg" className="flex-[2]" disabled={saving} onClick={() => void save()}>
            <Check size={16} /> {saving ? "Saving…" : contactId ? "Save changes" : "Save contact"}
          </Button>
        </div>
      }
    >
      <div className="space-y-5 px-5 pb-8 pt-5">
        {/* photo */}
        <div className="flex flex-col items-center gap-2">
          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="relative flex h-[84px] w-[84px] items-center justify-center overflow-hidden rounded-full border border-line2 bg-surface shadow-key transition active:scale-95"
            aria-label={t("ce.setPhoto")}
          >
            {photoPreview ? (
              <img src={photoPreview} alt="Contact photo" className="h-full w-full object-cover" />
            ) : (
              <Camera size={24} className="text-mute" />
            )}
          </button>
          <p className="text-[10px] font-medium text-mute">
            {photoPreview ? "Tap to change photo" : "Add photo"}
          </p>
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) {
                setPhotoFile(f);
                setPhotoPreview(URL.createObjectURL(f));
              }
              e.target.value = "";
            }}
          />
        </div>

        {/* names */}
        <div className="grid grid-cols-2 gap-2.5">
          <Field label={t("ce.firstName")}>
            <input
              className={inputCls}
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              placeholder="Abel"
            />
          </Field>
          <Field label={t("ce.lastName")}>
            <input
              className={inputCls}
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              placeholder="Tesfaye"
            />
          </Field>
        </div>
        <Field label={t("ce.nickname")}>
          <input
            className={inputCls}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder={t("ce.whatYouCallThem")}
          />
        </Field>
        <div className="grid grid-cols-2 gap-2.5">
          <Field label={t("ce.company")}>
            <input
              className={inputCls}
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              placeholder="Tesfaye Trading"
            />
          </Field>
          <Field label={t("ce.jobTitle")}>
            <input
              className={inputCls}
              value={jobTitle}
              onChange={(e) => setJobTitle(e.target.value)}
              placeholder="Manager"
            />
          </Field>
        </div>

        {/* phones — unlimited rows */}
        <section>
          <SectionLabel>{t("cd.phones")}</SectionLabel>
          <div className="space-y-2">
            {phones.map((p, i) => (
              <div key={p.id} className="flex items-center gap-2">
                <select
                  value={p.label}
                  onChange={(e) =>
                    setPhones((prev) => prev.map((x) => (x.id === p.id ? { ...x, label: e.target.value } : x)))
                  }
                  className={cx(inputCls, "w-[100px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Label for phone ${i + 1}`}
                >
                  {PHONE_LABELS.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
                <input
                  value={p.number}
                  onChange={(e) =>
                    setPhones((prev) => prev.map((x) => (x.id === p.id ? { ...x, number: e.target.value } : x)))
                  }
                  inputMode="tel"
                  placeholder={t("ce.phonePlaceholder")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Phone number ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() =>
                    setPhones((prev) => prev.map((x) => ({ ...x, primary: x.id === p.id })))
                  }
                  className={removeBtnCls}
                  aria-label={t("ce.setPrimaryNumber")}
                  title={t("ce.setPrimary")}
                >
                  <Star size={16} className={p.primary ? "text-brand" : "text-line2"} fill={p.primary ? "currentColor" : "none"} />
                </button>
                <button
                  type="button"
                  onClick={() => setPhones((prev) => prev.filter((x) => x.id !== p.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removePhone")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() =>
                setPhones((prev) => [
                  ...prev,
                  { id: uid(), label: "mobile", number: "", primary: prev.length === 0 },
                ])
              }
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addPhone")}
            </button>
          </div>
        </section>

        {/* emails */}
        <section>
          <SectionLabel>{t("cd.emails")}</SectionLabel>
          <div className="space-y-2">
            {emails.map((e, i) => (
              <div key={e.id} className="flex items-center gap-2">
                <select
                  value={e.label}
                  onChange={(ev) =>
                    setEmails((prev) => prev.map((x) => (x.id === e.id ? { ...x, label: ev.target.value } : x)))
                  }
                  className={cx(inputCls, "w-[100px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Label for email ${i + 1}`}
                >
                  {EMAIL_LABELS.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
                <input
                  value={e.address}
                  onChange={(ev) =>
                    setEmails((prev) => prev.map((x) => (x.id === e.id ? { ...x, address: ev.target.value } : x)))
                  }
                  inputMode="email"
                  placeholder={t("ce.emailPlaceholder")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Email address ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setEmails((prev) => prev.map((x) => ({ ...x, primary: x.id === e.id })))}
                  className={removeBtnCls}
                  aria-label={t("ce.setPrimaryEmail")}
                  title={t("ce.setPrimary")}
                >
                  <Star size={16} className={e.primary ? "text-brand" : "text-line2"} fill={e.primary ? "currentColor" : "none"} />
                </button>
                <button
                  type="button"
                  onClick={() => setEmails((prev) => prev.filter((x) => x.id !== e.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removeEmail")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setEmails((prev) => [...prev, { id: uid(), label: "home", address: "", primary: prev.length === 0 }])}
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addEmail")}
            </button>
          </div>
        </section>

        {/* socials */}
        <section>
          <SectionLabel>{t("cd.socials")}</SectionLabel>
          <div className="space-y-2">
            {socials.map((s, i) => (
              <div key={s.id} className="flex items-center gap-2">
                <select
                  value={s.app}
                  onChange={(e) =>
                    setSocials((prev) =>
                      prev.map((x) => (x.id === s.id ? { ...x, app: e.target.value as Social["app"] } : x))
                    )
                  }
                  className={cx(inputCls, "w-[110px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`App for social ${i + 1}`}
                >
                  {SOCIAL_APPS.map((a) => (
                    <option key={a} value={a}>
                      {a}
                    </option>
                  ))}
                </select>
                <input
                  value={s.handle}
                  onChange={(e) =>
                    setSocials((prev) => prev.map((x) => (x.id === s.id ? { ...x, handle: e.target.value } : x)))
                  }
                  placeholder={t("ce.socialPlaceholder")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Handle for social ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setSocials((prev) => prev.filter((x) => x.id !== s.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removeSocial")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setSocials((prev) => [...prev, { id: uid(), app: "telegram", handle: "" }])}
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addSocial")}
            </button>
          </div>
        </section>

        {/* links */}
        <section>
          <SectionLabel>{t("cd.links")}</SectionLabel>
          <div className="space-y-2">
            {links.map((l, i) => (
              <div key={l.id} className="flex items-center gap-2">
                <input
                  value={l.label}
                  onChange={(e) =>
                    setLinks((prev) => prev.map((x) => (x.id === l.id ? { ...x, label: e.target.value } : x)))
                  }
                  placeholder="Website"
                  className={cx(inputCls, "w-[100px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Label for link ${i + 1}`}
                />
                <input
                  value={l.url}
                  onChange={(e) =>
                    setLinks((prev) => prev.map((x) => (x.id === l.id ? { ...x, url: e.target.value } : x)))
                  }
                  inputMode="url"
                  placeholder={t("ce.websitePlaceholder")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`URL for link ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setLinks((prev) => prev.filter((x) => x.id !== l.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removeLink")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setLinks((prev) => [...prev, { id: uid(), label: "", url: "" }])}
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addLink")}
            </button>
          </div>
        </section>

        {/* addresses */}
        <section>
          <SectionLabel>{t("cd.addresses")}</SectionLabel>
          <div className="space-y-2">
            {addresses.map((a, i) => (
              <div key={a.id} className="flex items-center gap-2">
                <select
                  value={a.label}
                  onChange={(e) =>
                    setAddresses((prev) =>
                      prev.map((x) => (x.id === a.id ? { ...x, label: e.target.value } : x))
                    )
                  }
                  className={cx(inputCls, "w-[100px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Label for address ${i + 1}`}
                >
                  {ADDRESS_LABELS.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
                <input
                  value={a.text}
                  onChange={(e) =>
                    setAddresses((prev) => prev.map((x) => (x.id === a.id ? { ...x, text: e.target.value } : x)))
                  }
                  placeholder={t("ce.addressPlaceholder")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Address ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setAddresses((prev) => prev.filter((x) => x.id !== a.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removeAddress")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setAddresses((prev) => [...prev, { id: uid(), label: "home", text: "" }])}
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addAddress")}
            </button>
          </div>
        </section>

        {/* custom fields */}
        <section>
          <SectionLabel>{t("cd.customFields")}</SectionLabel>
          <div className="space-y-2">
            {customFields.map((f, i) => (
              <div key={f.id} className="flex items-center gap-2">
                <input
                  value={f.key}
                  onChange={(e) =>
                    setCustomFields((prev) => prev.map((x) => (x.id === f.id ? { ...x, key: e.target.value } : x)))
                  }
                  placeholder={t("ce.field")}
                  className={cx(inputCls, "w-[110px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Custom field name ${i + 1}`}
                />
                <input
                  value={f.value}
                  onChange={(e) =>
                    setCustomFields((prev) => prev.map((x) => (x.id === f.id ? { ...x, value: e.target.value } : x)))
                  }
                  placeholder={t("ce.value")}
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Custom field value ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setCustomFields((prev) => prev.filter((x) => x.id !== f.id))}
                  className={removeBtnCls}
                  aria-label={t("ce.removeField")}
                >
                  <X size={16} />
                </button>
              </div>
            ))}
            <button
              type="button"
              onClick={() => setCustomFields((prev) => [...prev, { id: uid(), key: "", value: "" }])}
              className={addBtnCls}
            >
              <Plus size={15} /> {t("ce.addField")}
            </button>
          </div>
        </section>

        {/* birthday + tags */}
        <div className="grid grid-cols-2 gap-2.5">
          <Field label={t("ce.birthdayLabel")}>
            <input
              type="date"
              value={birthday}
              onChange={(e) => setBirthday(e.target.value)}
              className={inputCls}
              aria-label={t("ce.birthdayLabel")}
            />
          </Field>
          <Field label={t("ce.tags")} hint={t("ce.tagsHint")}>
            <input
              value={tagsStr}
              onChange={(e) => setTagsStr(e.target.value)}
              placeholder={t("ce.tagsPlaceholder")}
              className={inputCls}
              aria-label={`${t("ce.tags")} · ${t("ce.tagsHint")}`}
            />
          </Field>
        </div>

        {/* favorite */}
        <div className="flex items-center justify-between rounded-2xl border border-line bg-surface px-4 py-3.5 shadow-key">
          <div className="flex items-center gap-2.5">
            <Star size={16} className="text-brand" fill={favorite ? "currentColor" : "none"} />
            <div>
              <p className="text-[13px] font-semibold text-ink">{t("cd.favorites")}</p>
              <p className="text-[10px] text-mute">{t("cd.pinFavorites")}</p>
            </div>
          </div>
          <SwitchToggle checked={favorite} onChange={setFavorite} label={t("cd.favorites")} />
        </div>

        {/* note */}
        <Field label={t("ce.note")}>
          <textarea
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={t("ce.notePlaceholder")}
            className={cx(inputCls, "resize-none")}
            aria-label={t("ce.note")}
          />
        </Field>
      </div>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* CallScreen                                                           */
/* ------------------------------------------------------------------ */

type CallPhase = "incoming" | "dialing" | "connected" | "ended" | "handingoff" | "handedoff";

function ControlButton({
  icon,
  label,
  onClick,
  active,
  tone,
  big,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  active?: boolean;
  tone?: "pos" | "neg";
  big?: boolean;
}) {
  return (
    <div className="flex flex-col items-center gap-1.5">
      <button
        type="button"
        onClick={onClick}
        aria-label={label}
        aria-pressed={active}
        className={cx(
          "flex items-center justify-center rounded-full transition active:scale-90",
          big ? "h-[68px] w-[68px]" : "h-[54px] w-[54px]",
          tone === "neg"
            ? "bg-neg text-white shadow-floating"
            : tone === "pos"
              ? "bg-brand text-white shadow-floating"
              : active
                ? "bg-brand text-white shadow-key"
                : "border border-line bg-surface text-[#62716e] shadow-key dark:text-ink"
        )}
      >
        {icon}
      </button>
      <span className="text-[10px] font-semibold text-mute">{label}</span>
    </div>
  );
}

export function CallScreen({
  phone,
  contactId,
  direction,
}: {
  phone: string;
  contactId?: ID;
  direction?: "incoming" | "outgoing" | "missed";
}) {
  const app = useApp();
  const ui = useUI();
  const t = useT();
  const cc = app.settings.countryCode;

  /* real-call handoff: outgoing calls (dialer mode = default) continue in the
     phone's default caller app; "demo" mode keeps the in-app simulation. */
  const dialerHandoff = direction !== "incoming" && app.settings.callMode !== "demo";

  /* hide while another overlay sits on top of the call (e.g. adding a contact mid-call) */
  const stack = useUI((s) => s.overlayStack);
  const isTop = stack.length > 0 && stack[stack.length - 1].type === "call";

  const contact = useMemo(() => {
    const byId = contactId ? app.contacts.find((c) => c.id === contactId && !c.deletedAt) : undefined;
    if (byId) return byId;
    const norm = normalizePhone(phone, cc);
    return (
      app.contacts.find((c) =>
        !c.deletedAt && c.phones.some((p) => normalizePhone(p.number, cc) === norm)
      ) ?? undefined
    );
  }, [app.contacts, contactId, phone, cc]);

  const [phase, setPhase] = useState<CallPhase>(
    direction === "incoming" ? "incoming" : dialerHandoff ? "handingoff" : "dialing"
  );
  const [seconds, setSeconds] = useState(0);
  const [muted, setMuted] = useState(false);
  const [speaker, setSpeaker] = useState(false);
  const [keypadOn, setKeypadOn] = useState(false);
  const [dtmf, setDtmf] = useState("");
  const [closePending, setClosePending] = useState(false);
  const finalizedRef = useRef(false);
  const handoffFiredRef = useRef(false);
  const isTopRef = useRef(isTop);
  useEffect(() => {
    isTopRef.current = isTop;
  }, [isTop]);

  /* dialing → connected after 1.5s (demo mode only) */
  useEffect(() => {
    if (phase !== "dialing") return;
    const t = setTimeout(() => setPhase("connected"), 1500);
    return () => clearTimeout(t);
  }, [phase]);

  /* live timer */
  useEffect(() => {
    if (phase !== "connected") return;
    const i = setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => clearInterval(i);
  }, [phase]);

  /** Write the call log entry — guarded so a call is logged exactly once. */
  const finalize = (dur: number, dir: CallLog["direction"]) => {
    if (finalizedRef.current) return;
    finalizedRef.current = true;
    app.addCall({ phone, contactId: contact?.id ?? contactId, direction: dir, duration: dur });
  };

  const closeScreen = () => {
    if (isTopRef.current) ui.pop();
    else setClosePending(true);
  };

  const logAndClose = (dur: number, dir: CallLog["direction"]) => {
    finalize(dur, dir);
    closeScreen();
  };

  /* seamless handoff: let the in-app call screen slide in (~450ms), then hand
     the call to the phone's default caller app exactly once and log it right
     away (duration 0) so the outgoing call always lands in the call log. */
  useEffect(() => {
    if (phase !== "handingoff") return;
    const t = setTimeout(() => {
      if (!handoffFiredRef.current) {
        handoffFiredRef.current = true;
        openTel(phone, cc);
      }
      setPhase("handedoff");
      finalize(0, direction ?? "outgoing");
    }, 450);
    return () => clearTimeout(t);
  }, [phase]);

  /* "Call ended" shows briefly, then the screen closes */
  useEffect(() => {
    if (phase !== "ended") return;
    const t = setTimeout(
      () => logAndClose(seconds, direction === "incoming" ? "incoming" : direction ?? "outgoing"),
      1400
    );
    return () => clearTimeout(t);
  }, [phase]);

  /* call ended while another overlay was open — close once we're back on top */
  useEffect(() => {
    if (isTop && closePending) ui.pop();
  }, [isTop, closePending]);

  if (!isTop) return null;

  const name = contact ? displayName(contact) : prettyPhone(phone, cc);
  const status =
    phase === "incoming"
      ? t("cl.incoming")
      : phase === "dialing"
        ? t("cl.calling")
        : phase === "connected"
          ? clockStr(seconds)
          : phase === "handingoff"
            ? t("cl.handingOff")
            : phase === "handedoff"
              ? t("cl.continuedInDialer")
              : direction === "incoming"
                ? t("cl.callDeclined")
                : `${t("cl.callEnded")} · ${clockStr(seconds)}`;

  const answer = () => {
    vibrate(15);
    setPhase("connected");
  };
  const decline = () => {
    vibrate(20);
    /* declined simulated incoming → logged as an incoming call with 0 duration */
    logAndClose(0, "incoming");
  };
  const end = () => {
    vibrate(20);
    if (phase === "incoming") {
      decline();
      return;
    }
    if (phase === "handingoff" || phase === "handedoff") {
      /* the call was already logged at handoff time — just close */
      finalize(0, direction ?? "outgoing");
      closeScreen();
      return;
    }
    setPhase("ended");
  };

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.98 }}
      transition={{ duration: 0.2 }}
      className="absolute inset-0 z-50 flex flex-col"
      style={{ background: "var(--app-canvas)" }}
      role="dialog"
      aria-label={`Call with ${name}`}
    >
      {/* subtle gradient */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-gradient-to-b from-brandsoft/70 via-transparent to-transparent"
      />

      <div className="app-scroll relative z-[1] flex flex-1 flex-col items-center overflow-y-auto px-6 pb-[max(24px,env(safe-area-inset-bottom))] pt-[max(56px,env(safe-area-inset-top))]">
        {/* hero */}
        <div className="flex shrink-0 flex-col items-center pt-2">
          <div
            className={cx(
              "rounded-[26px]",
              (phase === "dialing" || phase === "incoming" || phase === "handingoff") && "pulse-ring"
            )}
          >
            <Avatar contact={contact} name={contact ? undefined : "?"} size="xl" />
          </div>
          <h2 className="mt-4 text-[24px] font-bold tracking-tight text-ink">{name}</h2>
          <p className="mt-1 text-[12px] font-medium text-mute" aria-live="polite">
            {status}
          </p>
          {!contact && phase !== "ended" && (
            <p className="mt-1.5 rounded-full bg-surface2 px-2.5 py-0.5 text-[9px] font-bold uppercase tracking-[1.5px] text-mute">
              {t("cl.unknownNumber")}
            </p>
          )}
        </div>

        {/* in-call keypad (DTMF — cosmetic) */}
        {keypadOn && phase !== "incoming" && phase !== "ended" && phase !== "handingoff" && phase !== "handedoff" && (
          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
            className="mt-6 w-full max-w-[280px] shrink-0"
          >
            <div className="mb-2.5 flex h-10 items-center justify-center rounded-2xl bg-surface2 text-[15px] font-semibold tracking-[3px] tabular-nums text-ink shadow-key">
              {dtmf || <span className="text-[10px] tracking-[2px] text-mute">{t("cl.dtmf")}</span>}
            </div>
            <div className="grid grid-cols-3 gap-2">
              {["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"].map((d) => (
                <button
                  key={d}
                  type="button"
                  onClick={() => {
                    vibrate(6);
                    setDtmf((s) => (s + d).slice(0, 18));
                  }}
                  className="flex h-11 items-center justify-center rounded-2xl bg-surface text-lg font-semibold text-ink shadow-key transition active:scale-95"
                  aria-label={d}
                >
                  {d}
                </button>
              ))}
            </div>
          </motion.div>
        )}

        {/* controls */}
        <div className="mt-auto flex w-full shrink-0 flex-col items-center gap-7 pt-8">
          {phase === "incoming" && (
            <div className="flex items-center gap-16">
              <ControlButton big tone="neg" label={t("cl.decline")} icon={<PhoneOff size={26} />} onClick={decline} />
              <ControlButton big tone="pos" label={t("cl.answer")} icon={<PhoneCall size={26} />} onClick={answer} />
            </div>
          )}

          {(phase === "dialing" || phase === "connected") && (
            <>
              <div className="flex flex-wrap items-start justify-center gap-x-7 gap-y-4">
                <ControlButton
                  label={muted ? t("cl.unmute") : t("cl.mute")}
                  active={muted}
                  icon={muted ? <MicOff size={20} /> : <Mic size={20} />}
                  onClick={() => setMuted((m) => !m)}
                />
                <ControlButton
                  label={t("cl.keypad")}
                  active={keypadOn}
                  icon={<Grid3X3 size={20} />}
                  onClick={() => setKeypadOn((k) => !k)}
                />
                <ControlButton
                  label={speaker ? t("cl.speakerOff") : t("cl.speaker")}
                  active={speaker}
                  icon={speaker ? <Volume2 size={20} /> : <VolumeX size={20} />}
                  onClick={() => setSpeaker((s) => !s)}
                />
                <ControlButton
                  label={t("act.message")}
                  icon={<MessageCircle size={20} />}
                  onClick={() => toastOk(t("cl.messagingDemo"))}
                />
                {!contact && (
                  <ControlButton
                    label={t("act.newContact")}
                    icon={<UserPlus size={20} />}
                    onClick={() => ui.open({ type: "contactEdit", prefillPhone: phone })}
                  />
                )}
              </div>

              <button
                type="button"
                onClick={end}
                aria-label={t("cl.endCall")}
                className="mx-auto flex h-[68px] w-[68px] items-center justify-center rounded-full bg-neg text-white shadow-floating transition hover:bg-neg/90 active:scale-90"
              >
                <PhoneOff size={27} />
              </button>
            </>
          )}

          {/* dialer handoff — the call lives in the phone's caller app, so the
              mute / speaker / DTMF controls stay hidden; add-contact remains. */}
          {(phase === "handingoff" || phase === "handedoff") && (
            <>
              {!contact && (
                <div className="flex flex-wrap items-start justify-center gap-x-7 gap-y-4">
                  <ControlButton
                    label={t("act.newContact")}
                    icon={<UserPlus size={20} />}
                    onClick={() => ui.open({ type: "contactEdit", prefillPhone: phone })}
                  />
                </div>
              )}

              <button
                type="button"
                onClick={end}
                aria-label={t("cl.endCall")}
                className="mx-auto flex h-[68px] w-[68px] items-center justify-center rounded-full bg-neg text-white shadow-floating transition hover:bg-neg/90 active:scale-90"
              >
                <PhoneOff size={27} />
              </button>
            </>
          )}

          {phase === "ended" && <div className="h-[70px]" aria-hidden />}

          {/* real-device handoff */}
          {phase !== "ended" && (
            <div className="flex items-center justify-center gap-2.5">
              <button
                type="button"
                onClick={() => {
                  /* fire (or re-fire) the dialer handoff; the guard stops the
                     auto-timer from opening the dialer a second time. */
                  handoffFiredRef.current = true;
                  openTel(phone, cc);
                }}
                className="flex h-11 items-center gap-2 rounded-full border border-line2 bg-surface px-4 text-[11px] font-bold text-ink shadow-key transition active:scale-95"
                aria-label={phase === "handedoff" ? t("cl.openDialerAgain") : t("cl.phoneDialer")}
              >
                <Phone size={14} className="text-brand" />{" "}
                {phase === "handedoff" ? t("cl.openDialerAgain") : t("cl.phoneDialer")}
              </button>
              <button
                type="button"
                onClick={() => openExternal(waLink(phone, undefined, cc))}
                className="flex h-11 items-center gap-2 rounded-full border border-line2 bg-surface px-4 text-[11px] font-bold text-ink shadow-key transition active:scale-95"
                aria-label={t("act.whatsapp")}
              >
                <MessageCircle size={14} className="text-[#1faa53]" /> {t("act.whatsapp")}
              </button>
            </div>
          )}
        </div>
      </div>
    </motion.div>
  );
}

/* ------------------------------------------------------------------ */
/* MergeOverlay                                                         */
/* ------------------------------------------------------------------ */

export function MergeOverlay() {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const t = useT();
  const [pairs, setPairs] = useState(() => app.findDuplicates());
  const [dismissed, setDismissed] = useState<string[]>([]);

  const visible = pairs.filter((p) => !dismissed.includes(`${p.primary.id}:${p.dup.id}`));

  const rescan = () => {
    setPairs(app.findDuplicates());
    setDismissed([]);
  };

  const merge = async (primaryId: string, dupId: string) => {
    const primary = app.contacts.find((c) => c.id === primaryId);
    const dup = app.contacts.find((c) => c.id === dupId);
    if (!primary || !dup) return;
    const ok = await confirm({
      title: t("spd.mergeTitle"),
      message: t("spd.mergeMsg", { a: displayName(dup), b: displayName(primary) }),
      confirmLabel: "Merge",
      tone: "brand",
    });
    if (!ok) return;
    app.mergeContacts(primaryId, dupId);
    toastOk(t("spd.contactsMerged"));
    setPairs(app.findDuplicates());
  };

  return (
    <Sheet
      title={t("spd.mergeDuplicates")}
      subtitle={visible.length > 0 ? `${visible.length} pair${visible.length === 1 ? "" : "s"} found` : undefined}
      onBack={ui.pop}
      actions={
        <IconButton label={t("spd.rescan")} onClick={rescan}>
          <RefreshCw size={17} />
        </IconButton>
      }
    >
      <div className="px-5 pb-10 pt-4">
        {visible.length === 0 ? (
          <EmptyState
            icon={<Sparkles size={22} />}
            title={t("spd.noDuplicates")}
            hint={t("spd.everyUnique")}
          />
        ) : (
          <div className="space-y-4">
            {visible.map((p) => {
              const primaryPhoneNo = primaryPhone(p.primary) ?? p.primary.emails[0]?.address;
              const dupPhoneNo = primaryPhone(p.dup) ?? p.dup.emails[0]?.address;
              return (
                <SectionCard key={`${p.primary.id}:${p.dup.id}`} className="space-y-3">
                  <div className="flex items-center gap-3">
                    <Avatar contact={p.primary} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13px] font-semibold text-ink">{displayName(p.primary)}</p>
                      <p className="truncate text-[11px] text-mute">{primaryPhoneNo || "—"}</p>
                    </div>
                    <span className="shrink-0 rounded-full bg-brandsoft px-2.5 py-1 text-[9px] font-bold uppercase tracking-wide text-brand">
                      Keep
                    </span>
                  </div>

                  <div className="flex items-center justify-center gap-2 rounded-full bg-surface2 px-3 py-1.5">
                    <ArrowDown size={12} className="shrink-0 text-brand" />
                    <span className="text-[10px] font-semibold text-mute">{p.reason}</span>
                  </div>

                  <div className="flex items-center gap-3 opacity-80">
                    <Avatar contact={p.dup} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-[13px] font-semibold text-ink">{displayName(p.dup)}</p>
                      <p className="truncate text-[11px] text-mute">{dupPhoneNo || "—"}</p>
                    </div>
                    <span className="shrink-0 rounded-full bg-negsoft px-2.5 py-1 text-[9px] font-bold uppercase tracking-wide text-neg">
                      Duplicate
                    </span>
                  </div>

                  <div className="flex gap-2 pt-0.5">
                    <Button
                      variant="surface"
                      size="md"
                      className="min-w-0 flex-1"
                      onClick={() =>
                        setDismissed((d) => [...d, `${p.primary.id}:${p.dup.id}`])
                      }
                    >
                      Dismiss pair
                    </Button>
                    <Button
                      variant="brand"
                      size="md"
                      className="min-w-0 flex-[1.4]"
                      onClick={() => void merge(p.primary.id, p.dup.id)}
                    >
                      <GitMerge size={14} className="shrink-0" />
                      <span className="truncate">
                        Merge into {p.primary.firstName || displayName(p.primary)}
                      </span>
                    </Button>
                  </div>
                </SectionCard>
              );
            })}
            <button
              type="button"
              onClick={rescan}
              className="flex h-11 w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-line2 bg-surface2/60 text-[12px] font-semibold text-brand transition active:scale-[0.98]"
            >
              <RefreshCw size={14} /> {t("spd.rescan")}
            </button>
          </div>
        )}
      </div>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* SpeedDialOverlay                                                     */
/* ------------------------------------------------------------------ */

export function SpeedDialOverlay() {
  const app = useApp();
  const ui = useUI();
  const t = useT();
  const [pickKey, setPickKey] = useState<string | null>(null);
  const [menuKey, setMenuKey] = useState<string | null>(null);

  const contacts = useMemo(() => app.contacts.filter((c) => !c.deletedAt), [app.contacts]);
  const sd = speedDialMap(app.settings);

  const assigned = useMemo(() => {
    const out: Record<string, string | undefined> = {};
    contacts.forEach((c) => {
      if (c.speedDial) out[c.speedDial] = c.id;
    });
    SPEED_KEYS.forEach((k) => {
      if (k in sd) out[k] = sd[k];
    });
    return out;
  }, [contacts, sd]);

  const assign = (key: string, contactId: string) => {
    const patch: SettingsPatch = { speedDial: { ...sd, [key]: contactId } as Record<string, string> };
    app.updateSettings(patch);
    toastOk(t("kp.speedDialSet", { key }));
    setPickKey(null);
    setMenuKey(null);
  };

  const removeKey = (key: string) => {
    const next = { ...sd };
    delete next[key];
    const patch: SettingsPatch = { speedDial: next as Record<string, string> };
    app.updateSettings(patch);
    toastOk(t("kp.speedDialCleared", { key }));
    setMenuKey(null);
  };

  const menuContact = menuKey ? contacts.find((c) => c.id === assigned[menuKey]) : undefined;

  return (
    <Sheet title={t("spd.speedDial")} subtitle={t("spd.oneLongPress")} onBack={ui.pop}>
      <div className="px-5 pb-10 pt-4">
        <div className="grid grid-cols-4 gap-2.5">
          {SPEED_KEYS.map((k) => {
            const c = contacts.find((x) => x.id === assigned[k]);
            return (
              <button
                key={k}
                type="button"
                onClick={() => (c ? setMenuKey(k) : setPickKey(k))}
                className="relative flex aspect-square flex-col items-center justify-center gap-1.5 rounded-[20px] border border-line bg-surface shadow-key transition active:scale-95"
                aria-label={c ? `Key ${k}: ${displayName(c)}` : `Assign a contact to key ${k}`}
              >
                <span className="absolute left-2.5 top-2 text-[9px] font-bold text-mute">{k}</span>
                {c ? (
                  <>
                    <Avatar contact={c} size="sm" />
                    <span className="max-w-full truncate px-1.5 text-[10px] font-semibold text-ink">
                      {displayName(c)}
                    </span>
                  </>
                ) : (
                  <>
                    <span className="text-[26px] font-medium leading-7 text-ink">{k}</span>
                    <span className="text-[8px] font-semibold tracking-[2px] text-mute">{KEY_LETTERS[k]}</span>
                  </>
                )}
              </button>
            );
          })}
        </div>

        <div className="mt-5 flex items-center gap-2.5 rounded-2xl bg-brandsoft px-4 py-3.5">
          <Info size={15} className="shrink-0 text-brand" />
          <p className="text-[11px] font-medium leading-4 text-ink">
            Long-press a key on the Keypad to speed dial — even faster than tapping.
          </p>
        </div>
      </div>

      {/* assign picker */}
      <ContactPicker
        open={!!pickKey}
        onClose={() => setPickKey(null)}
        onPick={(contactId) => pickKey && assign(pickKey, contactId)}
        allowGeneral={false}
        title={pickKey ? `Assign key ${pickKey}` : "Choose a contact"}
      />

      {/* assigned-key menu */}
      <BottomSheet
        open={!!menuKey}
        onClose={() => setMenuKey(null)}
        title={menuContact && menuKey ? `Key ${menuKey} · ${displayName(menuContact)}` : ""}
      >
        {menuContact && menuKey && (
          <div className="space-y-0.5 pb-2">
            <ActionRow
              icon={<Phone size={17} />}
              title="Call now"
              sub={primaryPhone(menuContact) ? prettyPhone(primaryPhone(menuContact)!) : t("cd.noPhone")}
              onClick={() => {
                const p = primaryPhone(menuContact);
                setMenuKey(null);
                if (!p) {
                  toastErr(t("cd.noPhone"));
                  return;
                }
                ui.open({ type: "call", phone: p, contactId: menuContact.id });
              }}
            />
            <ActionRow
              icon={<UserRound size={17} />}
              title={t("spd.replace")}
              onClick={() => {
                const k = menuKey;
                setMenuKey(null);
                setPickKey(k);
              }}
            />
            <ActionRow
              icon={<Ban size={17} />}
              title="Remove from key"
              danger
              onClick={() => removeKey(menuKey)}
            />
          </div>
        )}
      </BottomSheet>
    </Sheet>
  );
}
