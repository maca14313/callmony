"use client";

/* =========================================================================
   callmony — Contacts views (Task 2-a)
   ContactsView · FavoritesView · RecentsView
   ========================================================================= */

import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  ArrowDownLeft,
  ArrowDownUp,
  ArrowUpRight,
  Ban,
  ChevronRight,
  Copy,
  Grid3X3,
  MessageCircle,
  MessageSquare,
  MoreVertical,
  Pencil,
  Phone,
  PhoneIncoming,
  PhoneMissed,
  Plus,
  Send,
  Share2,
  Star,
  Trash2,
  UserPlus,
  UserRound,
  Users,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { ownerFullBalance, r2 } from "@/lib/calc";
import { toVCard } from "@/lib/exporters";
import {
  copyText,
  dayGroup,
  displayName,
  downloadBlob,
  durationStr,
  ft,
  normalizePhone,
  prettyPhone,
} from "@/lib/utils";
import {
  openExternal,
  primaryPhone,
  shareText,
  smsLink,
  telegramHandle,
  tgLink,
  waLink,
} from "@/lib/share";
import type { CallLog, Contact, Settings } from "@/lib/types";
import {
  Avatar,
  BalancePill,
  Button,
  Chip,
  EmptyState,
  IconButton,
  Money,
  SearchInput,
  SectionCard,
  SectionLabel,
  cx,
} from "../kit/primitives";
import { BottomSheet } from "../kit/Sheet";
import { SwipeRow, type SwipeAction } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { toastErr, toastOk, toastUndoLast } from "../kit/toast";

/* ------------------------------------------------------------------ */
/* Shared bits                                                          */
/* ------------------------------------------------------------------ */

const SPEED_KEYS = ["2", "3", "4", "5", "6", "7", "8", "9"];

/**
 * Speed-dial assignment per key. `settings.speedDial` wins; contacts that
 * still carry a legacy `speedDial` field act as a fallback (demo data).
 */
function speedDialAssignments(contacts: Contact[], settings: Settings): Record<string, string | undefined> {
  const out: Record<string, string | undefined> = {};
  contacts.forEach((c) => {
    if (c.speedDial) out[c.speedDial] = c.id;
  });
  const sd = (settings as Settings & { speedDial?: Record<string, string | undefined> }).speedDial ?? {};
  SPEED_KEYS.forEach((k) => {
    if (k in sd) out[k] = sd[k];
  });
  return out;
}

/** Share a contact as a vCard — Web Share when available, copy / download otherwise. */
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

/** A tappable action row for bottom-sheet menus. */
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

function ViewHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-4 flex items-start justify-between gap-3">
      <div>
        <h1 className="text-[25px] font-bold tracking-tight text-ink">{title}</h1>
        <p className="mt-0.5 text-[12px] text-mute">{subtitle}</p>
      </div>
      {action}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* ContactsView                                                         */
/* ------------------------------------------------------------------ */

type FilterKey = "all" | "owe-me" | "i-owe" | "settled" | "favorites";
type SortKey = "name" | "balance" | "recent";

const SORT_LABEL: Record<SortKey, string> = { name: "A–Z", balance: "Balance", recent: "Recent" };

function ContactRow({
  contact,
  balance,
  blocked,
  onOpen,
  onCall,
  onFavorite,
  onWhatsApp,
  onMessage,
  onDelete,
  onLongPress,
}: {
  contact: Contact;
  balance: number;
  blocked: boolean;
  onOpen: () => void;
  onCall: () => void;
  onFavorite: () => void;
  onWhatsApp: () => void;
  onMessage: () => void;
  onDelete: () => void;
  onLongPress: () => void;
}) {
  const phone = primaryPhone(contact);
  const sub = phone
    ? prettyPhone(phone)
    : contact.socials.some((s) => s.app === "telegram")
      ? "Telegram only"
      : contact.emails[0]?.address || "No phone number";

  const left: SwipeAction[] = [
    { key: "call", icon: <Phone size={18} />, label: "Call", className: "bg-brand", onClick: onCall },
    {
      key: "fav",
      icon: <Star size={18} fill={contact.favorite ? "currentColor" : "none"} />,
      label: contact.favorite ? "Unfavorite" : "Favorite",
      className: "bg-brandink",
      onClick: onFavorite,
    },
  ];
  const right: SwipeAction[] = [
    {
      key: "wa",
      icon: <MessageCircle size={18} />,
      label: "WhatsApp",
      className: "bg-[#1faa53]",
      onClick: onWhatsApp,
    },
    { key: "sms", icon: <MessageSquare size={18} />, label: "Message", className: "bg-brandink", onClick: onMessage },
    { key: "del", icon: <Trash2 size={18} />, label: "Delete contact", className: "bg-neg", onClick: onDelete },
  ];

  return (
    <SwipeRow className="mb-1.5" left={left} right={right} onTap={onOpen} onLongPress={onLongPress}>
      <div className="flex items-center gap-3 rounded-2xl bg-surface px-3.5 py-2.5 shadow-key">
        <Avatar contact={contact} size="md" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <p className="truncate text-[13px] font-semibold text-ink">{displayName(contact)}</p>
            {contact.favorite && (
              <Star size={11} className="shrink-0 text-brand" fill="currentColor" aria-label="Favorite" />
            )}
            {blocked && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-negsoft px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide text-neg">
                <Ban size={9} /> blocked
              </span>
            )}
          </div>
          <p className="truncate text-[11px] text-mute">{sub}</p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <BalancePill value={balance} size="sm" />
          <ChevronRight size={14} className="text-line2" />
        </div>
      </div>
    </SwipeRow>
  );
}

export function ContactsView() {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<FilterKey>("all");
  const [sort, setSort] = useState<SortKey>("name");
  const [menuFor, setMenuFor] = useState<Contact | null>(null);

  const cc = app.settings.countryCode;
  const contacts = useMemo(() => app.contacts.filter((c) => !c.deletedAt), [app.contacts]);

  const balances = useMemo(() => {
    const map = new Map<string, number>();
    contacts.forEach((c) => map.set(c.id, ownerFullBalance(app.entries, app.lists, c.id)));
    return map;
  }, [contacts, app.entries, app.lists]);

  const lastActivity = useMemo(() => {
    const map = new Map<string, number>();
    contacts.forEach((c) => map.set(c.id, c.createdAt));
    app.entries.forEach((e) => {
      if (!e.deletedAt) map.set(e.ownerId, Math.max(map.get(e.ownerId) ?? 0, e.updatedAt));
    });
    app.lists.forEach((l) => {
      if (!l.deletedAt) map.set(l.ownerId, Math.max(map.get(l.ownerId) ?? 0, l.updatedAt));
    });
    app.recents.forEach((r) => {
      if (r.contactId) map.set(r.contactId, Math.max(map.get(r.contactId) ?? 0, r.at));
    });
    return map;
  }, [contacts, app.entries, app.lists, app.recents]);

  const { toReceive, toPay, net } = useMemo(() => {
    let pos = 0;
    let neg = 0;
    contacts.forEach((c) => {
      const b = balances.get(c.id) ?? 0;
      if (b > 0) pos += b;
      if (b < 0) neg += b;
    });
    return { toReceive: r2(pos), toPay: r2(neg), net: r2(pos + neg) };
  }, [contacts, balances]);

  const blockedSet = useMemo(() => new Set(app.settings.blockedNumbers), [app.settings.blockedNumbers]);
  const isBlocked = (c: Contact) => c.phones.some((p) => blockedSet.has(normalizePhone(p.number, cc)));

  const filtered = useMemo(() => {
    const query = q.toLowerCase().trim();
    const digits = query.replace(/[^\d]/g, "");
    const list = contacts.filter((c) => {
      const b = balances.get(c.id) ?? 0;
      if (filter === "owe-me" && b <= 0.005) return false;
      if (filter === "i-owe" && b >= -0.005) return false;
      if (filter === "settled" && Math.abs(b) > 0.005) return false;
      if (filter === "favorites" && !c.favorite) return false;
      if (!query) return true;
      const hay = [
        displayName(c),
        c.nickname || "",
        c.company || "",
        c.jobTitle || "",
        c.note || "",
        c.pinnedNote || "",
        ...c.tags,
        ...c.phones.map((p) => p.number),
        ...c.emails.map((e) => e.address),
        ...c.socials.map((s) => s.handle),
      ]
        .join(" ")
        .toLowerCase();
      const numberHit =
        digits.length >= 2 && c.phones.some((p) => p.number.replace(/\D/g, "").includes(digits));
      return hay.includes(query) || numberHit;
    });
    return [...list].sort((a, b) => {
      if (sort === "balance")
        return (balances.get(b.id) ?? 0) - (balances.get(a.id) ?? 0) || displayName(a).localeCompare(displayName(b));
      if (sort === "recent")
        return (
          (lastActivity.get(b.id) ?? 0) - (lastActivity.get(a.id) ?? 0) ||
          displayName(a).localeCompare(displayName(b))
        );
      return displayName(a).localeCompare(displayName(b));
    });
  }, [contacts, q, filter, sort, balances, lastActivity]);

  const groups = useMemo(() => {
    if (sort !== "name") return [{ letter: "", items: filtered }];
    const out: { letter: string; items: Contact[] }[] = [];
    filtered.forEach((c) => {
      const ch = (displayName(c)[0] || "#").toUpperCase();
      const letter = /[A-Z]/.test(ch) ? ch : "#";
      const last = out[out.length - 1];
      if (last && last.letter === letter) last.items.push(c);
      else out.push({ letter, items: [c] });
    });
    return out;
  }, [filtered, sort]);

  /* ---- actions ---- */
  const call = (c: Contact) => {
    const p = primaryPhone(c);
    if (!p) {
      toastErr("No phone number for this contact");
      return;
    }
    ui.open({ type: "call", phone: p, contactId: c.id });
  };
  const whatsApp = (c: Contact) => {
    const p = primaryPhone(c);
    if (!p) {
      toastErr("No phone number on this contact");
      return;
    }
    openExternal(waLink(p, undefined, cc));
  };
  const message = (c: Contact) => {
    const p = primaryPhone(c);
    if (!p) {
      toastErr("No phone number on this contact");
      return;
    }
    openExternal(smsLink(p, undefined, cc));
  };
  const remove = async (c: Contact) => {
    const ok = await confirm({
      title: "Delete contact?",
      message: `“${displayName(c)}” will move to Trash. You can undo right after, and ledger history stays safe.`,
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    app.deleteContact(c.id);
    toastUndoLast();
  };
  const toggleBlock = (c: Contact) => {
    const nums = c.phones.map((p) => normalizePhone(p.number, cc));
    if (!nums.length) {
      toastErr("No number to block");
      return;
    }
    const blocked = nums.every((n) => blockedSet.has(n));
    const next = blocked
      ? app.settings.blockedNumbers.filter((b) => !nums.includes(b))
      : [...new Set([...app.settings.blockedNumbers, ...nums])];
    app.updateSettings({ blockedNumbers: next });
    setMenuFor(null);
    toastOk(blocked ? "Number unblocked" : "Number blocked");
  };
  const cycleSort = () =>
    setSort((s) => (s === "name" ? "balance" : s === "balance" ? "recent" : "name"));

  const menu = menuFor;
  const menuPhone = menu ? primaryPhone(menu) : undefined;
  const menuTg = menu ? telegramHandle(menu) : undefined;

  return (
    <div className="tab-content">
      <ViewHeader title="Contacts" subtitle="The people who matter." />

      <SearchInput value={q} onChange={setQ} placeholder="Search name, number, notes…" />

      {/* summary strip — only when balances exist */}
      {(toReceive > 0.005 || toPay < -0.005) && (
        <div className="mt-3 grid grid-cols-3 gap-2">
          <div className="rounded-2xl border border-line bg-surface p-3 shadow-key">
            <p className="text-[9px] font-bold uppercase tracking-[1.5px] text-mute">To receive</p>
            <p className="mt-1 text-[13px] font-bold text-pos">
              <Money value={toReceive} signed />
            </p>
          </div>
          <div className="rounded-2xl border border-line bg-surface p-3 shadow-key">
            <p className="text-[9px] font-bold uppercase tracking-[1.5px] text-mute">To pay</p>
            <p className="mt-1 text-[13px] font-bold text-neg">
              <Money value={toPay} signed />
            </p>
          </div>
          <div className="rounded-2xl border border-line bg-surface p-3 shadow-key">
            <p className="text-[9px] font-bold uppercase tracking-[1.5px] text-mute">Net</p>
            <p
              className={cx(
                "mt-1 text-[13px] font-bold",
                net > 0.005 ? "text-pos" : net < -0.005 ? "text-neg" : "text-ink"
              )}
            >
              <Money value={net} signed />
            </p>
          </div>
        </div>
      )}

      {/* filter chips */}
      <div className="app-scroll -mx-1 mt-3 flex gap-1.5 overflow-x-auto px-1 pb-1">
        <Chip className="py-2" active={filter === "all"} onClick={() => setFilter("all")}>
          All
        </Chip>
        <Chip className="py-2" active={filter === "owe-me"} onClick={() => setFilter("owe-me")}>
          They owe me
        </Chip>
        <Chip className="py-2" active={filter === "i-owe"} onClick={() => setFilter("i-owe")}>
          I owe them
        </Chip>
        <Chip className="py-2" active={filter === "settled"} onClick={() => setFilter("settled")}>
          Settled
        </Chip>
        <Chip className="py-2" active={filter === "favorites"} onClick={() => setFilter("favorites")}>
          Favorites
        </Chip>
      </div>

      <div className="mt-4">
        <SectionLabel
          right={
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={cycleSort}
                className="flex h-11 items-center gap-1.5 rounded-full border border-line2 bg-surface px-3.5 text-[10px] font-bold uppercase tracking-[1.2px] text-mute shadow-key transition active:scale-95"
                aria-label={`Sort by ${SORT_LABEL[sort]} — tap to change`}
              >
                <ArrowDownUp size={13} className="text-brand" />
                {SORT_LABEL[sort]}
              </button>
              <button
                type="button"
                onClick={() => ui.open({ type: "contactEdit" })}
                className="flex h-11 items-center gap-1 rounded-full bg-brandsoft px-3.5 text-[11px] font-bold text-brand transition active:scale-95"
                aria-label="Add new contact"
              >
                <Plus size={14} /> Add new
              </button>
            </div>
          }
        >
          {filtered.length === contacts.length
            ? `${contacts.length} contact${contacts.length === 1 ? "" : "s"}`
            : `${filtered.length} of ${contacts.length}`}
        </SectionLabel>

        {filtered.length === 0 ? (
          <EmptyState
            icon={<Users size={22} />}
            title={q || filter !== "all" ? "No matching contacts" : "No contacts yet"}
            hint={
              q || filter !== "all"
                ? "Try a different search or filter."
                : "Add the people you call and settle with most."
            }
            action={
              contacts.length === 0 ? (
                <Button variant="brand" onClick={() => ui.open({ type: "contactEdit" })}>
                  <Plus size={15} /> Add contact
                </Button>
              ) : undefined
            }
          />
        ) : (
          <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.22 }}>
            {groups.map((g) => (
              <div key={g.letter || "all"}>
                {g.letter && (
                  <div className="sticky top-0 z-10 bg-canvas/95 px-1 pb-1.5 pt-2 backdrop-blur-sm">
                    <p className="text-[11px] font-bold uppercase tracking-[2px] text-brand">{g.letter}</p>
                  </div>
                )}
                {g.items.map((c) => (
                  <ContactRow
                    key={c.id}
                    contact={c}
                    balance={balances.get(c.id) ?? 0}
                    blocked={isBlocked(c)}
                    onOpen={() => ui.open({ type: "contact", contactId: c.id })}
                    onCall={() => call(c)}
                    onFavorite={() => {
                      app.toggleFavorite(c.id);
                      toastOk(c.favorite ? "Removed from favorites" : "Added to favorites");
                    }}
                    onWhatsApp={() => whatsApp(c)}
                    onMessage={() => message(c)}
                    onDelete={() => void remove(c)}
                    onLongPress={() => setMenuFor(c)}
                  />
                ))}
              </div>
            ))}
          </motion.div>
        )}
      </div>

      {/* long-press context menu */}
      <BottomSheet
        open={!!menu}
        onClose={() => setMenuFor(null)}
        title={menu ? displayName(menu) : ""}
      >
        {menu && (
          <div className="space-y-0.5 pb-2">
            <ActionRow
              icon={<Phone size={17} />}
              title="Call"
              sub={menuPhone ? prettyPhone(menuPhone, cc) : "No phone number"}
              onClick={() => {
                setMenuFor(null);
                call(menu);
              }}
            />
            <ActionRow
              icon={<MessageCircle size={17} />}
              title="WhatsApp"
              sub={menuPhone ? prettyPhone(menuPhone, cc) : undefined}
              onClick={() => {
                setMenuFor(null);
                whatsApp(menu);
              }}
            />
            {menuTg && (
              <ActionRow
                icon={<Send size={17} />}
                title="Telegram"
                sub={`@${menuTg.replace(/^@/, "")}`}
                onClick={() => {
                  setMenuFor(null);
                  openExternal(tgLink(menuTg));
                }}
              />
            )}
            <ActionRow
              icon={<Pencil size={17} />}
              title="Edit contact"
              onClick={() => {
                setMenuFor(null);
                ui.open({ type: "contactEdit", contactId: menu.id });
              }}
            />
            <ActionRow
              icon={<Star size={17} fill={menu.favorite ? "currentColor" : "none"} />}
              title={menu.favorite ? "Remove from favorites" : "Add to favorites"}
              onClick={() => {
                app.toggleFavorite(menu.id);
                setMenuFor(null);
                toastOk(menu.favorite ? "Removed from favorites" : "Added to favorites");
              }}
            />
            <ActionRow
              icon={<Share2 size={17} />}
              title="Share vCard"
              onClick={() => {
                setMenuFor(null);
                void shareVCard(menu);
              }}
            />
            <ActionRow
              icon={<Ban size={17} />}
              title={isBlocked(menu) ? "Unblock number" : "Block number"}
              onClick={() => toggleBlock(menu)}
            />
            <ActionRow
              icon={<Trash2 size={17} />}
              title="Delete contact"
              danger
              onClick={() => {
                setMenuFor(null);
                void remove(menu);
              }}
            />
          </div>
        )}
      </BottomSheet>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* FavoritesView                                                        */
/* ------------------------------------------------------------------ */

export function FavoritesView() {
  const app = useApp();
  const ui = useUI();

  const favorites = useMemo(
    () =>
      app.contacts
        .filter((c) => !c.deletedAt && c.favorite)
        .sort((a, b) => displayName(a).localeCompare(displayName(b))),
    [app.contacts]
  );

  const sdContacts = useMemo(() => {
    const live = app.contacts.filter((c) => !c.deletedAt);
    const map = speedDialAssignments(live, app.settings);
    return SPEED_KEYS.map((k) => map[k])
      .filter((id): id is string => !!id)
      .map((id) => live.find((c) => c.id === id))
      .filter((c): c is Contact => !!c);
  }, [app.contacts, app.settings]);

  return (
    <div className="tab-content">
      <ViewHeader title="Favorites" subtitle="Your closest connections, one tap away." />

      {favorites.length === 0 ? (
        <EmptyState
          icon={<Star size={22} />}
          title="No favorites yet"
          hint="Star the people you reach for most and they'll live here — plus a speed-dial key."
          action={
            <Button variant="brand" onClick={() => ui.setTab("contacts")}>
              <Users size={15} /> Explore contacts
            </Button>
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3">
            {favorites.map((c, i) => {
              const phone = primaryPhone(c);
              return (
                <motion.div
                  key={c.id}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04, duration: 0.24 }}
                >
                  <SectionCard
                    className="flex flex-col items-center gap-2.5 text-center"
                    onClick={() => ui.open({ type: "contact", contactId: c.id })}
                  >
                    <div className="relative">
                      <Avatar contact={c} size="lg" />
                      <span className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-brand text-white shadow-key">
                        <Star size={11} fill="currentColor" />
                      </span>
                    </div>
                    <div className="w-full">
                      <p className="truncate text-[13px] font-semibold text-ink">{displayName(c)}</p>
                      <p className="truncate text-[11px] text-mute">
                        {phone ? prettyPhone(phone, app.settings.countryCode) : "Telegram only"}
                      </p>
                    </div>
                    {phone ? (
                      <Button
                        variant="brand"
                        size="sm"
                        className="w-full"
                        onClick={(e) => {
                          e.stopPropagation();
                          ui.open({ type: "call", phone, contactId: c.id });
                        }}
                      >
                        <Phone size={13} /> Call now
                      </Button>
                    ) : (
                      <Button
                        variant="soft"
                        size="sm"
                        className="w-full"
                        onClick={(e) => {
                          e.stopPropagation();
                          ui.open({ type: "contact", contactId: c.id });
                        }}
                      >
                        <UserRound size={13} /> Open profile
                      </Button>
                    )}
                  </SectionCard>
                </motion.div>
              );
            })}
          </div>

          <div className="mt-5">
            <SectionLabel>Shortcuts</SectionLabel>
            <SectionCard
              className="flex items-center gap-3"
              onClick={() => ui.open({ type: "speedDial" })}
            >
              <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-brandsoft text-brand">
                <Grid3X3 size={20} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[13px] font-bold text-ink">Speed dial</p>
                <p className="truncate text-[11px] text-mute">
                  {sdContacts.length > 0
                    ? `${sdContacts.length} contact${sdContacts.length === 1 ? "" : "s"} on keys 2–9`
                    : "Assign contacts to keys 2–9"}
                </p>
              </div>
              {sdContacts.length > 0 && (
                <div className="flex shrink-0 -space-x-2">
                  {sdContacts.slice(0, 4).map((c) => (
                    <Avatar
                      key={c.id}
                      contact={c}
                      size="xs"
                      className="border-2 border-surface"
                    />
                  ))}
                </div>
              )}
              <ChevronRight size={16} className="shrink-0 text-line2" />
            </SectionCard>
          </div>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* RecentsView                                                          */
/* ------------------------------------------------------------------ */

type RecentFilter = "all" | "missed" | "incoming" | "outgoing";

const DIR_META: Record<CallLog["direction"], { label: string; icon: React.ReactNode }> = {
  outgoing: { label: "Outgoing", icon: <ArrowUpRight size={12} className="text-brand" /> },
  incoming: { label: "Incoming", icon: <ArrowDownLeft size={12} className="text-mute" /> },
  missed: { label: "Missed", icon: <PhoneMissed size={12} className="text-neg" /> },
};

function RecentRow({
  call,
  contact,
  onOpen,
  onRedial,
}: {
  call: CallLog;
  contact?: Contact;
  onOpen: () => void;
  onRedial: () => void;
}) {
  const name = contact ? displayName(contact) : prettyPhone(call.phone);
  const dir = DIR_META[call.direction];
  return (
    <div className="mb-1.5 flex items-center gap-3 rounded-2xl bg-surface px-3.5 py-2.5 shadow-key">
      <button
        type="button"
        onClick={onOpen}
        className="flex min-w-0 flex-1 items-center gap-3 text-left"
        aria-label={`Open ${name}`}
      >
        {contact ? <Avatar contact={contact} size="md" /> : <Avatar name="?" size="md" />}
        <span className="min-w-0 flex-1">
          <span
            className={cx(
              "block truncate text-[13px] font-semibold",
              call.direction === "missed" ? "text-neg" : "text-ink"
            )}
          >
            {name}
          </span>
          <span className="flex items-center gap-1.5 text-[11px] text-mute">
            {dir.icon}
            <span>{dir.label}</span>
            {call.duration > 0 && (
              <>
                <span className="text-line2">·</span>
                <span className="tabular-nums">{durationStr(call.duration)}</span>
              </>
            )}
          </span>
        </span>
      </button>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <span className="text-[10px] font-medium text-mute">{ft(call.at)}</span>
        <IconButton label={`Call ${name}`} variant="surface" className="h-9 w-9" onClick={onRedial}>
          <Phone size={15} />
        </IconButton>
      </div>
    </div>
  );
}

export function RecentsView() {
  const app = useApp();
  const ui = useUI();
  const confirm = useConfirm();
  const [q, setQ] = useState("");
  const [dir, setDir] = useState<RecentFilter>("all");
  const [menuOpen, setMenuOpen] = useState(false);
  const [unknownFor, setUnknownFor] = useState<CallLog | null>(null);

  const cc = app.settings.countryCode;
  const contacts = useMemo(() => app.contacts.filter((c) => !c.deletedAt), [app.contacts]);

  const contactFor = useMemo(() => {
    const byId = new Map(contacts.map((c) => [c.id, c] as const));
    const byPhone = new Map<string, Contact>();
    contacts.forEach((c) =>
      c.phones.forEach((p) => byPhone.set(normalizePhone(p.number, cc), c))
    );
    return (r: CallLog) => byId.get(r.contactId ?? "") ?? byPhone.get(normalizePhone(r.phone, cc));
  }, [contacts, cc]);

  const recents = useMemo(() => [...app.recents].sort((a, b) => b.at - a.at), [app.recents]);

  const groups = useMemo(() => {
    const query = q.toLowerCase().trim();
    const digits = query.replace(/[^\d]/g, "");
    const list = recents.filter((r) => {
      if (dir !== "all" && r.direction !== dir) return false;
      if (!query) return true;
      const c = contactFor(r);
      const hay = `${c ? displayName(c) : ""} ${r.phone}`.toLowerCase();
      return hay.includes(query) || (digits.length >= 2 && r.phone.replace(/\D/g, "").includes(digits));
    });
    const out: { label: string; items: CallLog[] }[] = [];
    list.forEach((r) => {
      const label = dayGroup(r.at);
      const last = out[out.length - 1];
      if (last && last.label === label) last.items.push(r);
      else out.push({ label, items: [r] });
    });
    return out;
  }, [recents, q, dir, contactFor]);

  const missedCount = useMemo(() => recents.filter((r) => r.direction === "missed").length, [recents]);

  const redial = (r: CallLog) => {
    const c = contactFor(r);
    ui.open({ type: "call", phone: r.phone, contactId: c?.id });
  };

  const unknown = unknownFor;

  return (
    <div className="tab-content">
      <ViewHeader
        title="Recent calls"
        subtitle="Your conversations at a glance."
        action={
          <IconButton
            label="Call history options"
            className="mt-1"
            onClick={() => setMenuOpen(true)}
          >
            <MoreVertical size={18} />
          </IconButton>
        }
      />

      <SearchInput value={q} onChange={setQ} placeholder="Search calls…" />

      <div className="app-scroll -mx-1 mt-3 flex gap-1.5 overflow-x-auto px-1 pb-1">
        <Chip className="py-2" active={dir === "all"} onClick={() => setDir("all")}>
          All
        </Chip>
        <Chip className="py-2" active={dir === "missed"} onClick={() => setDir("missed")}>
          Missed{missedCount > 0 ? ` · ${missedCount}` : ""}
        </Chip>
        <Chip className="py-2" active={dir === "incoming"} onClick={() => setDir("incoming")}>
          Incoming
        </Chip>
        <Chip className="py-2" active={dir === "outgoing"} onClick={() => setDir("outgoing")}>
          Outgoing
        </Chip>
      </div>

      <div className="mt-4">
        {groups.length === 0 ? (
          <EmptyState
            icon={<PhoneMissed size={22} />}
            title={recents.length === 0 ? "No calls yet" : "No matching calls"}
            hint={
              recents.length === 0
                ? "Calls you make from callmony will appear here."
                : "Try a different search or filter."
            }
          />
        ) : (
          <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.22 }}>
            {groups.map((g) => (
              <div key={g.label} className="mb-2">
                <div className="sticky top-0 z-10 bg-canvas/95 px-1 pb-1.5 pt-2 backdrop-blur-sm">
                  <p className="text-[11px] font-bold uppercase tracking-[2px] text-brand">{g.label}</p>
                </div>
                {g.items.map((r) => (
                  <RecentRow
                    key={r.id}
                    call={r}
                    contact={contactFor(r)}
                    onOpen={() => {
                      const c = contactFor(r);
                      if (c) ui.open({ type: "contact", contactId: c.id });
                      else setUnknownFor(r);
                    }}
                    onRedial={() => redial(r)}
                  />
                ))}
              </div>
            ))}
          </motion.div>
        )}
      </div>

      {/* history options */}
      <BottomSheet open={menuOpen} onClose={() => setMenuOpen(false)} title="Call history">
        <div className="space-y-0.5 pb-2">
          <ActionRow
            icon={<PhoneIncoming size={17} />}
            title="Simulate incoming call"
            sub="See how an incoming call feels"
            onClick={() => {
              setMenuOpen(false);
              ui.open({ type: "call", phone: "+251 900 000 000", direction: "incoming" });
            }}
          />
          <ActionRow
            icon={<Trash2 size={17} />}
            title="Clear call history"
            danger
            onClick={async () => {
              setMenuOpen(false);
              const ok = await confirm({
                title: "Clear call history?",
                message: "All recent calls will be removed. This cannot be undone.",
                confirmLabel: "Clear all",
                tone: "danger",
              });
              if (!ok) return;
              app.clearRecents();
              toastOk("Call history cleared");
            }}
          />
        </div>
      </BottomSheet>

      {/* unknown number sheet */}
      <BottomSheet
        open={!!unknown}
        onClose={() => setUnknownFor(null)}
        title={unknown ? prettyPhone(unknown.phone) : ""}
      >
        {unknown && (
          <div className="space-y-0.5 pb-2">
            <ActionRow
              icon={<Phone size={17} />}
              title="Call"
              onClick={() => {
                setUnknownFor(null);
                ui.open({ type: "call", phone: unknown.phone });
              }}
            />
            <ActionRow
              icon={<UserPlus size={17} />}
              title="Add to contacts"
              onClick={() => {
                const phone = unknown.phone;
                setUnknownFor(null);
                ui.open({ type: "contactEdit", prefillPhone: phone });
              }}
            />
            <ActionRow
              icon={<Copy size={17} />}
              title="Copy number"
              onClick={() => {
                void copyText(unknown.phone).then((ok) => toastOk(ok ? "Number copied" : "Couldn't copy"));
                setUnknownFor(null);
              }}
            />
            <ActionRow
              icon={<Trash2 size={17} />}
              title="Delete this log entry"
              danger
              onClick={async () => {
                const ok = await confirm({
                  title: "Delete log entry?",
                  message: "This single call record will be removed.",
                  confirmLabel: "Delete",
                  tone: "danger",
                });
                if (!ok) return;
                app.deleteRecent(unknown.id);
                setUnknownFor(null);
                toastOk("Call log entry deleted");
              }}
            />
          </div>
        )}
      </BottomSheet>
    </div>
  );
}
