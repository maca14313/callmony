"use client";

/* =========================================================================
   dial. — Contacts & calls overlays (Task 2-a)
   ContactDetail · ContactEdit · CallScreen · MergeOverlay · SpeedDialOverlay
   ========================================================================= */

import { useEffect, useMemo, useRef, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDown,
  Ban,
  Building2,
  Cake,
  Camera,
  Check,
  ChevronRight,
  Copy,
  GitMerge,
  Grid3X3,
  Info,
  Link2,
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
  X,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { openListsPending, ownerFullBalance } from "@/lib/calc";
import { toVCard } from "@/lib/exporters";
import {
  clockStr,
  copyText,
  displayName,
  downloadBlob,
  fd,
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
  primaryEmail,
  primaryPhone,
  reminderTemplates,
  shareText,
  smsLink,
  telLink,
  telegramHandle,
  tgLink,
  waLink,
} from "@/lib/share";
import type {
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
import { AttachButton, AttachmentStrip, FilesOfOwner } from "../kit/Attachments";
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
    toastOk("vCard copied to clipboard");
  } else if (res === "failed") {
    downloadBlob(new Blob([vcf], { type: "text/vcard" }), `${displayName(c)}.vcf`);
    toastOk("vCard downloaded");
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
/* ContactDetail                                                        */
/* ------------------------------------------------------------------ */

type DetailTab = "overview" | "ledger" | "lists" | "notes" | "bank" | "files" | "reminders";

export function ContactDetail({ contactId }: { contactId: ID }) {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const [tab, setTab] = useState<DetailTab>("overview");
  const [menuOpen, setMenuOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const [phoneSheet, setPhoneSheet] = useState<PhoneT | null>(null);

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

  if (!contact) {
    return (
      <Sheet title="Contact" onBack={ui.pop}>
        <div className="p-5">
          <EmptyState
            icon={<UserRound size={22} />}
            title="Contact not found"
            hint="It may have been deleted or merged into another contact."
          />
        </div>
      </Sheet>
    );
  }

  const settled = Math.abs(balance) < 0.005;
  const balanceLabel = settled ? "Settled up" : balance > 0 ? "They owe you" : "You owe them";
  const phone = primaryPhone(contact);
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

  const openWhatsApp = () => {
    if (!phone) {
      toastErr("No phone number on this contact");
      return;
    }
    openExternal(waLink(phone, waReminder, cc));
    if (waReminder) app.logMessage(contact.id, "whatsapp", waReminder);
  };

  const remove = async () => {
    const ok = await confirm({
      title: "Delete contact?",
      message: `“${name}” will move to Trash. Ledger history stays safe and can be re-linked later.`,
      confirmLabel: "Delete",
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
    toastOk("Primary number updated");
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
            label={contact.favorite ? "Remove from favorites" : "Add to favorites"}
            variant={contact.favorite ? "brand" : "ghost"}
            onClick={() => {
              app.toggleFavorite(contact.id);
              toastOk(contact.favorite ? "Removed from favorites" : "Added to favorites");
            }}
          >
            <Star size={17} fill={contact.favorite ? "currentColor" : "none"} />
          </IconButton>
          <IconButton label="More actions" onClick={() => setMenuOpen(true)}>
            <MoreVertical size={18} />
          </IconButton>
        </>
      }
    >
      <div className="px-5 pb-10 pt-4">
        {/* hero */}
        <div className="flex flex-col items-center">
          <Avatar contact={contact} size="xl" />
          <h2 className="mt-3 text-[22px] font-bold tracking-tight text-ink">{name}</h2>
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
              Open lists <Money value={openPending} signed /> counted in balance
            </p>
          )}
        </div>

        {/* quick actions */}
        <div className="app-scroll -mx-1 mt-5 flex gap-2 overflow-x-auto px-1 pb-1">
          <QuickAction
            primary
            icon={<PhoneCall size={19} />}
            label="Call"
            disabled={!phone}
            onClick={() => phone && ui.open({ type: "call", phone, contactId: contact.id })}
          />
          <QuickAction
            icon={<MessageCircle size={19} />}
            label="SMS"
            disabled={!phone}
            onClick={() => phone && openExternal(smsLink(phone, undefined, cc))}
          />
          <QuickAction
            icon={<MessageCircle size={19} />}
            label="WhatsApp"
            disabled={!phone}
            onClick={openWhatsApp}
          />
          {tg && (
            <QuickAction icon={<Send size={19} />} label="Telegram" onClick={() => openExternal(tgLink(tg))} />
          )}
          {email && (
            <QuickAction
              icon={<Mail size={19} />}
              label="Email"
              onClick={() => openExternal(mailtoLink(email))}
            />
          )}
          <QuickAction icon={<MoreVertical size={19} />} label="More" onClick={() => setMoreOpen(true)} />
        </div>

        {/* tabs */}
        <div className="mt-5">
          <Segmented
            value={tab}
            onChange={setTab}
            options={[
              { value: "overview", label: "Overview" },
              { value: "ledger", label: "Ledger" },
              { value: "lists", label: "Lists" },
              { value: "notes", label: "Notes" },
              { value: "bank", label: "Bank" },
              { value: "files", label: "Files" },
              { value: "reminders", label: "Reminders" },
            ]}
          />

          <div className="mt-4 space-y-4">
            {tab === "ledger" && <LedgerSection ownerId={contact.id} />}
            {tab === "lists" && <ListsSection ownerId={contact.id} />}
            {tab === "notes" && <NotesSection ownerId={contact.id} />}
            {tab === "bank" && <BankSection ownerId={contact.id} />}
            {tab === "files" && <FilesOfOwner ownerId={contact.id} />}
            {tab === "reminders" && <RemindersSection ownerId={contact.id} />}
            {tab === "reminders" && (
              <p className="text-center text-[10px] text-mute">
                Reminders for {name} · birthdays fire automatically
              </p>
            )}

            {tab === "overview" && (
              <motion.div
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.2 }}
                className="space-y-4"
              >
                {/* phones */}
                {contact.phones.length > 0 && (
                  <section>
                    <SectionLabel>Phones</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.phones.map((p) => (
                        <button
                          key={p.id}
                          type="button"
                          onClick={() => setPhoneSheet(p)}
                          className="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                          aria-label={`Options for ${prettyPhone(p.number, cc)}`}
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
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* emails */}
                {contact.emails.length > 0 && (
                  <section>
                    <SectionLabel>Emails</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.emails.map((e) => (
                        <div key={e.id} className="flex items-center gap-3 px-4 py-3">
                          <LabelChip>{e.label}</LabelChip>
                          <button
                            type="button"
                            className="min-w-0 flex-1 truncate text-left text-[13px] font-semibold text-ink"
                            onClick={() => {
                              void copyText(e.address).then((ok) => toastOk(ok ? "Email copied" : "Couldn't copy"));
                            }}
                            aria-label="Copy email address"
                          >
                            {e.address}
                          </button>
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
                    <SectionLabel>Socials</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.socials.map((s) => (
                        <button
                          key={s.id}
                          type="button"
                          onClick={() => {
                            const url = socialUrl(s);
                            if (url) openExternal(url);
                            else {
                              void copyText(s.handle).then((ok) => toastOk(ok ? "Handle copied" : "Couldn't copy"));
                            }
                          }}
                          className="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                          aria-label={`Open ${s.app}`}
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
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* links */}
                {contact.links.length > 0 && (
                  <section>
                    <SectionLabel>Links</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.links.map((l) => (
                        <button
                          key={l.id}
                          type="button"
                          onClick={() => openExternal(l.url)}
                          className="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                          aria-label={`Open ${l.label || l.url}`}
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
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* addresses */}
                {contact.addresses.length > 0 && (
                  <section>
                    <SectionLabel>Addresses</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.addresses.map((a) => (
                        <button
                          key={a.id}
                          type="button"
                          onClick={() => {
                            void copyText(a.text).then((ok) => toastOk(ok ? "Address copied" : "Couldn't copy"));
                          }}
                          className="flex w-full items-center gap-3 px-4 py-3 text-left transition active:bg-surface2"
                          aria-label="Copy address"
                        >
                          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[13px] bg-brandsoft text-brand">
                            <MapPin size={15} />
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-[13px] font-semibold text-ink">{a.text}</span>
                            <span className="block text-[10px] uppercase tracking-[1.5px] text-mute">{a.label}</span>
                          </span>
                          <Copy size={13} className="shrink-0 text-line2" />
                        </button>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* birthday */}
                {contact.birthday && (
                  <section>
                    <SectionLabel>Birthday</SectionLabel>
                    <SectionCard className="flex items-center gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-brandsoft text-brand">
                        <Cake size={17} />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-[13px] font-semibold text-ink">{fd(contact.birthday)}</p>
                        {birthdayIn !== null && (
                          <p className="text-[11px] text-mute">
                            {birthdayIn === 0
                              ? "Today — happy birthday!"
                              : birthdayIn === 1
                                ? "Tomorrow"
                                : `In ${birthdayIn} days`}
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
                    <SectionLabel>Custom fields</SectionLabel>
                    <SectionCard className="divide-y divide-line p-0">
                      {contact.customFields.map((f) => (
                        <div key={f.id} className="flex items-center gap-3 px-4 py-3">
                          <span className="w-[92px] shrink-0 truncate text-[10px] font-bold uppercase tracking-[1.2px] text-mute">
                            {f.key}
                          </span>
                          <span className="min-w-0 flex-1 truncate text-[13px] font-semibold text-ink">{f.value}</span>
                        </div>
                      ))}
                    </SectionCard>
                  </section>
                )}

                {/* tags */}
                {contact.tags.length > 0 && (
                  <section>
                    <SectionLabel>Tags</SectionLabel>
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
                    <SectionLabel>Notes</SectionLabel>
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

                {/* attachments */}
                <section>
                  <SectionLabel
                    right={
                      <AttachButton parentType="contact" parentId={contact.id} compact label="Attach" />
                    }
                  >
                    Files
                  </SectionLabel>
                  <AttachmentStrip parentType="contact" parentId={contact.id} />
                  {!app.attachments.some(
                    (a) => a.parentType === "contact" && a.parentId === contact.id
                  ) && (
                    <p className="px-1 text-[10px] text-mute">
                      Attach ID scans, agreements or voice notes — they live on this contact.
                    </p>
                  )}
                </section>
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
            title="Edit contact"
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "contactEdit", contactId: contact.id });
            }}
          />
          <ActionRow
            icon={<Share2 size={17} />}
            title="Share vCard"
            onClick={() => {
              setMenuOpen(false);
              void shareVCard(contact);
            }}
          />
          <ActionRow
            icon={<GitMerge size={17} />}
            title="Merge duplicates"
            sub="Scan for similar contacts"
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
      <BottomSheet open={moreOpen} onClose={() => setMoreOpen(false)} title="More">
        <div className="space-y-0.5 pb-2">
          <ActionRow
            icon={<Share2 size={17} />}
            title="Share vCard"
            onClick={() => {
              setMoreOpen(false);
              void shareVCard(contact);
            }}
          />
          {phone && (
            <ActionRow
              icon={<Copy size={17} />}
              title="Copy number"
              sub={prettyPhone(phone, cc)}
              onClick={() => {
                void copyText(phone).then((ok) => toastOk(ok ? "Number copied" : "Couldn't copy"));
                setMoreOpen(false);
              }}
            />
          )}
          {phone && (
            <ActionRow
              icon={<Phone size={17} />}
              title="Open in phone dialer"
              onClick={() => {
                setMoreOpen(false);
                openExternal(telLink(phone, cc));
              }}
            />
          )}
          {phone && (
            <ActionRow
              icon={<PhoneCall size={17} />}
              title="Simulate incoming call"
              onClick={() => {
                setMoreOpen(false);
                ui.open({ type: "call", phone, contactId: contact.id, direction: "incoming" });
              }}
            />
          )}
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
              title="Call"
              onClick={() => {
                const p = phoneSheet.number;
                setPhoneSheet(null);
                ui.open({ type: "call", phone: p, contactId: contact.id });
              }}
            />
            <ActionRow
              icon={<MessageCircle size={17} />}
              title="SMS"
              onClick={() => {
                openExternal(smsLink(phoneSheet.number, undefined, cc));
                setPhoneSheet(null);
              }}
            />
            <ActionRow
              icon={<MessageCircle size={17} />}
              title="WhatsApp"
              onClick={() => {
                openExternal(waLink(phoneSheet.number, undefined, cc));
                setPhoneSheet(null);
              }}
            />
            {hasTelegramSocial && tg && (
              <ActionRow
                icon={<Send size={17} />}
                title="Telegram"
                sub={`@${tg.replace(/^@/, "")}`}
                onClick={() => {
                  openExternal(tgLink(tg));
                  setPhoneSheet(null);
                }}
              />
            )}
            <ActionRow
              icon={<Copy size={17} />}
              title="Copy number"
              onClick={() => {
                void copyText(phoneSheet.number).then((ok) => toastOk(ok ? "Number copied" : "Couldn't copy"));
                setPhoneSheet(null);
              }}
            />
            {!phoneSheet.primary && (
              <ActionRow
                icon={<Star size={17} />}
                title="Set as primary"
                onClick={() => setPrimaryPhone(phoneSheet)}
              />
            )}
          </div>
        )}
      </BottomSheet>
    </Sheet>
  );
}

function QuickAction({
  icon,
  label,
  onClick,
  primary,
  disabled,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  primary?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="flex w-[64px] shrink-0 flex-col items-center gap-1.5 transition active:scale-95 disabled:opacity-40"
    >
      <span
        className={cx(
          "flex h-12 w-12 items-center justify-center rounded-[17px] shadow-key",
          primary ? "bg-brand text-white shadow-floating" : "border border-line bg-surface text-brand"
        )}
      >
        {icon}
      </span>
      <span className="text-[10px] font-semibold text-mute">{label}</span>
    </button>
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
      <Sheet title="Edit contact" onBack={ui.pop}>
        <div className="p-5">
          <EmptyState
            icon={<UserRound size={22} />}
            title="Contact not found"
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
      toastErr("Add at least a name or a phone number");
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
      title: "Delete contact?",
      message: `“${displayName(existing)}” will move to Trash. You can undo right after.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    app.deleteContact(existing.id);
    toastUndoLast();
    ui.pop();
  };

  return (
    <Sheet
      title={contactId ? "Edit contact" : "New contact"}
      subtitle={existing ? displayName(existing) : prefillPhone ? prettyPhone(prefillPhone) : undefined}
      onBack={ui.pop}
      footer={
        <div className="flex gap-2.5">
          {contactId && (
            <Button variant="surface" size="lg" className="flex-1 text-neg" onClick={() => void remove()}>
              <Trash2 size={16} /> Delete
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
            aria-label="Set contact photo"
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
          <Field label="First name">
            <input
              className={inputCls}
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              placeholder="Abel"
            />
          </Field>
          <Field label="Last name">
            <input
              className={inputCls}
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              placeholder="Tesfaye"
            />
          </Field>
        </div>
        <Field label="Nickname">
          <input
            className={inputCls}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="What you call them"
          />
        </Field>
        <div className="grid grid-cols-2 gap-2.5">
          <Field label="Company">
            <input
              className={inputCls}
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              placeholder="Tesfaye Trading"
            />
          </Field>
          <Field label="Job title">
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
          <SectionLabel>Phones</SectionLabel>
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
                  placeholder="+251 9…"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Phone number ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() =>
                    setPhones((prev) => prev.map((x) => ({ ...x, primary: x.id === p.id })))
                  }
                  className={removeBtnCls}
                  aria-label="Set as primary number"
                  title="Set as primary"
                >
                  <Star size={16} className={p.primary ? "text-brand" : "text-line2"} fill={p.primary ? "currentColor" : "none"} />
                </button>
                <button
                  type="button"
                  onClick={() => setPhones((prev) => prev.filter((x) => x.id !== p.id))}
                  className={removeBtnCls}
                  aria-label="Remove phone"
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
              <Plus size={15} /> Add phone
            </button>
          </div>
        </section>

        {/* emails */}
        <section>
          <SectionLabel>Emails</SectionLabel>
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
                  placeholder="name@example.com"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Email address ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setEmails((prev) => prev.map((x) => ({ ...x, primary: x.id === e.id })))}
                  className={removeBtnCls}
                  aria-label="Set as primary email"
                  title="Set as primary"
                >
                  <Star size={16} className={e.primary ? "text-brand" : "text-line2"} fill={e.primary ? "currentColor" : "none"} />
                </button>
                <button
                  type="button"
                  onClick={() => setEmails((prev) => prev.filter((x) => x.id !== e.id))}
                  className={removeBtnCls}
                  aria-label="Remove email"
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
              <Plus size={15} /> Add email
            </button>
          </div>
        </section>

        {/* socials */}
        <section>
          <SectionLabel>Socials</SectionLabel>
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
                  placeholder="@username or number"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Handle for social ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setSocials((prev) => prev.filter((x) => x.id !== s.id))}
                  className={removeBtnCls}
                  aria-label="Remove social"
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
              <Plus size={15} /> Add social
            </button>
          </div>
        </section>

        {/* links */}
        <section>
          <SectionLabel>Links</SectionLabel>
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
                  placeholder="https://…"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`URL for link ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setLinks((prev) => prev.filter((x) => x.id !== l.id))}
                  className={removeBtnCls}
                  aria-label="Remove link"
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
              <Plus size={15} /> Add link
            </button>
          </div>
        </section>

        {/* addresses */}
        <section>
          <SectionLabel>Addresses</SectionLabel>
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
                  placeholder="Street, city…"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Address ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setAddresses((prev) => prev.filter((x) => x.id !== a.id))}
                  className={removeBtnCls}
                  aria-label="Remove address"
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
              <Plus size={15} /> Add address
            </button>
          </div>
        </section>

        {/* custom fields */}
        <section>
          <SectionLabel>Custom fields</SectionLabel>
          <div className="space-y-2">
            {customFields.map((f, i) => (
              <div key={f.id} className="flex items-center gap-2">
                <input
                  value={f.key}
                  onChange={(e) =>
                    setCustomFields((prev) => prev.map((x) => (x.id === f.id ? { ...x, key: e.target.value } : x)))
                  }
                  placeholder="Field"
                  className={cx(inputCls, "w-[110px] shrink-0 px-3 py-2.5 text-xs")}
                  aria-label={`Custom field name ${i + 1}`}
                />
                <input
                  value={f.value}
                  onChange={(e) =>
                    setCustomFields((prev) => prev.map((x) => (x.id === f.id ? { ...x, value: e.target.value } : x)))
                  }
                  placeholder="Value"
                  className={cx(inputCls, "min-w-0 flex-1 px-3 py-2.5 text-[13px]")}
                  aria-label={`Custom field value ${i + 1}`}
                />
                <button
                  type="button"
                  onClick={() => setCustomFields((prev) => prev.filter((x) => x.id !== f.id))}
                  className={removeBtnCls}
                  aria-label="Remove custom field"
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
              <Plus size={15} /> Add field
            </button>
          </div>
        </section>

        {/* birthday + tags */}
        <div className="grid grid-cols-2 gap-2.5">
          <Field label="Birthday">
            <input
              type="date"
              value={birthday}
              onChange={(e) => setBirthday(e.target.value)}
              className={inputCls}
              aria-label="Birthday"
            />
          </Field>
          <Field label="Tags" hint="Comma separated">
            <input
              value={tagsStr}
              onChange={(e) => setTagsStr(e.target.value)}
              placeholder="supplier, vip"
              className={inputCls}
              aria-label="Tags, comma separated"
            />
          </Field>
        </div>

        {/* favorite */}
        <div className="flex items-center justify-between rounded-2xl border border-line bg-surface px-4 py-3.5 shadow-key">
          <div className="flex items-center gap-2.5">
            <Star size={16} className="text-brand" fill={favorite ? "currentColor" : "none"} />
            <div>
              <p className="text-[13px] font-semibold text-ink">Favorite</p>
              <p className="text-[10px] text-mute">Pin to your Favorites screen</p>
            </div>
          </div>
          <SwitchToggle checked={favorite} onChange={setFavorite} label="Toggle favorite" />
        </div>

        {/* note */}
        <Field label="Note">
          <textarea
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Notes about this person…"
            className={cx(inputCls, "resize-none")}
            aria-label="Note"
          />
        </Field>
      </div>
    </Sheet>
  );
}

/* ------------------------------------------------------------------ */
/* CallScreen                                                           */
/* ------------------------------------------------------------------ */

type CallPhase = "incoming" | "dialing" | "connected" | "ended";

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
  const cc = app.settings.countryCode;

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

  const [phase, setPhase] = useState<CallPhase>(direction === "incoming" ? "incoming" : "dialing");
  const [seconds, setSeconds] = useState(0);
  const [muted, setMuted] = useState(false);
  const [speaker, setSpeaker] = useState(false);
  const [keypadOn, setKeypadOn] = useState(false);
  const [dtmf, setDtmf] = useState("");
  const [closePending, setClosePending] = useState(false);
  const finalizedRef = useRef(false);
  const isTopRef = useRef(isTop);
  useEffect(() => {
    isTopRef.current = isTop;
  }, [isTop]);

  /* dialing → connected after 1.5s */
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

  const logAndClose = (dur: number, dir: CallLog["direction"]) => {
    if (finalizedRef.current) return;
    finalizedRef.current = true;
    app.addCall({ phone, contactId: contact?.id ?? contactId, direction: dir, duration: dur });
    if (isTopRef.current) ui.pop();
    else setClosePending(true);
  };

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
      ? "Incoming call…"
      : phase === "dialing"
        ? "Calling…"
        : phase === "connected"
          ? clockStr(seconds)
          : direction === "incoming"
            ? "Call declined"
            : `Call ended · ${clockStr(seconds)}`;

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
          <div className={cx("rounded-[26px]", (phase === "dialing" || phase === "incoming") && "pulse-ring")}>
            <Avatar contact={contact} name={contact ? undefined : "?"} size="xl" />
          </div>
          <h2 className="mt-4 text-[24px] font-bold tracking-tight text-ink">{name}</h2>
          <p className="mt-1 text-[12px] font-medium text-mute" aria-live="polite">
            {status}
          </p>
          {!contact && phase !== "ended" && (
            <p className="mt-1.5 rounded-full bg-surface2 px-2.5 py-0.5 text-[9px] font-bold uppercase tracking-[1.5px] text-mute">
              Unknown number
            </p>
          )}
        </div>

        {/* in-call keypad (DTMF — cosmetic) */}
        {keypadOn && phase !== "incoming" && phase !== "ended" && (
          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.2 }}
            className="mt-6 w-full max-w-[280px] shrink-0"
          >
            <div className="mb-2.5 flex h-10 items-center justify-center rounded-2xl bg-surface2 text-[15px] font-semibold tracking-[3px] tabular-nums text-ink shadow-key">
              {dtmf || <span className="text-[10px] tracking-[2px] text-mute">DTMF TONES</span>}
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
                  aria-label={`Keypad ${d}`}
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
              <ControlButton big tone="neg" label="Decline" icon={<PhoneOff size={26} />} onClick={decline} />
              <ControlButton big tone="pos" label="Answer" icon={<PhoneCall size={26} />} onClick={answer} />
            </div>
          )}

          {(phase === "dialing" || phase === "connected") && (
            <>
              <div className="flex flex-wrap items-start justify-center gap-x-7 gap-y-4">
                <ControlButton
                  label={muted ? "Unmute" : "Mute"}
                  active={muted}
                  icon={muted ? <MicOff size={20} /> : <Mic size={20} />}
                  onClick={() => setMuted((m) => !m)}
                />
                <ControlButton
                  label="Keypad"
                  active={keypadOn}
                  icon={<Grid3X3 size={20} />}
                  onClick={() => setKeypadOn((k) => !k)}
                />
                <ControlButton
                  label={speaker ? "Speaker off" : "Speaker"}
                  active={speaker}
                  icon={speaker ? <Volume2 size={20} /> : <VolumeX size={20} />}
                  onClick={() => setSpeaker((s) => !s)}
                />
                <ControlButton
                  label="Message"
                  icon={<MessageCircle size={20} />}
                  onClick={() => toastOk("Messaging isn't available during a call in this demo")}
                />
                {!contact && (
                  <ControlButton
                    label="Add contact"
                    icon={<UserPlus size={20} />}
                    onClick={() => ui.open({ type: "contactEdit", prefillPhone: phone })}
                  />
                )}
              </div>

              <button
                type="button"
                onClick={end}
                aria-label="End call"
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
                onClick={() => openExternal(telLink(phone, cc))}
                className="flex h-11 items-center gap-2 rounded-full border border-line2 bg-surface px-4 text-[11px] font-bold text-ink shadow-key transition active:scale-95"
                aria-label="Open in phone dialer"
              >
                <Phone size={14} className="text-brand" /> Phone dialer
              </button>
              <button
                type="button"
                onClick={() => openExternal(waLink(phone, undefined, cc))}
                className="flex h-11 items-center gap-2 rounded-full border border-line2 bg-surface px-4 text-[11px] font-bold text-ink shadow-key transition active:scale-95"
                aria-label="Open WhatsApp"
              >
                <MessageCircle size={14} className="text-[#1faa53]" /> WhatsApp
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
      title: "Merge contacts?",
      message: `“${displayName(dup)}” will be merged into “${displayName(primary)}”. Phones, emails, tags and all history are combined.`,
      confirmLabel: "Merge",
      tone: "brand",
    });
    if (!ok) return;
    app.mergeContacts(primaryId, dupId);
    toastOk("Contacts merged");
    setPairs(app.findDuplicates());
  };

  return (
    <Sheet
      title="Merge duplicates"
      subtitle={visible.length > 0 ? `${visible.length} pair${visible.length === 1 ? "" : "s"} found` : undefined}
      onBack={ui.pop}
      actions={
        <IconButton label="Rescan for duplicates" onClick={rescan}>
          <RefreshCw size={17} />
        </IconButton>
      }
    >
      <div className="px-5 pb-10 pt-4">
        {visible.length === 0 ? (
          <EmptyState
            icon={<Sparkles size={22} />}
            title="No duplicates found 🎉"
            hint="Every contact looks unique — same phone number or same name + email would land here."
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
              <RefreshCw size={14} /> Rescan
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
    toastOk(`Speed dial key ${key} set`);
    setPickKey(null);
    setMenuKey(null);
  };

  const removeKey = (key: string) => {
    const next = { ...sd };
    delete next[key];
    const patch: SettingsPatch = { speedDial: next as Record<string, string> };
    app.updateSettings(patch);
    toastOk(`Speed dial key ${key} cleared`);
    setMenuKey(null);
  };

  const menuContact = menuKey ? contacts.find((c) => c.id === assigned[menuKey]) : undefined;

  return (
    <Sheet title="Speed dial" subtitle="One long-press away" onBack={ui.pop}>
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
              sub={primaryPhone(menuContact) ? prettyPhone(primaryPhone(menuContact)!) : "No phone number"}
              onClick={() => {
                const p = primaryPhone(menuContact);
                setMenuKey(null);
                if (!p) {
                  toastErr("No phone number for this contact");
                  return;
                }
                ui.open({ type: "call", phone: p, contactId: menuContact.id });
              }}
            />
            <ActionRow
              icon={<UserRound size={17} />}
              title="Replace contact"
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
