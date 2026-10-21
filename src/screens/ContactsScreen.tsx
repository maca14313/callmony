/* Contacts tab — every phone contact in a VIRTUALIZED, searchable FlatList.
   Rows are memoized; only the visible ~15 rows are mounted at any time, so
   even a 2000-contact address book scrolls at 60fps. Sticky A–Z section
   headers, swipe actions, favorites, balances. */

import React, { memo, useCallback, useEffect, useMemo, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { Linking } from "react-native";
import { useApp } from "../lib/store";
import { listTotals } from "../lib/calc";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone, t9Match } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import {
  checkContactsPermission,
  fetchAllContacts,
  openExistingContactEditor,
  requestContactsPermission,
} from "../native/deviceContacts";
import { placeCall } from "../native/calls";
import { openURL, smsLink, waLink } from "../lib/share";
import { shareContactVcard } from "../lib/exporters";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Chip, EmptyState, Money, Pill, Spinner } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

type SortMode = "az" | "balance" | "recent";
type PermState = "loading" | "authorized" | "denied" | "undetermined";

const openSettings = async () => {
  try {
    await Linking.openSettings();
  } catch {
    /* noop */
  }
};

/* ---------------- one memoized contact row ---------------- */

interface RowProps {
  contact: DeviceContact;
  balance: number;
  isFav: boolean;
  isBlocked: boolean;
  countryCode: string;
  onTap: (recordID: string) => void;
  onLongPress: (recordID: string) => void;
  onCall: (phone: string) => void;
  onToggleFav: (recordID: string) => void;
  onWhatsApp: (phone: string) => void;
  onSMS: (phone: string) => void;
}

const ContactRow: React.FC<RowProps> = memo(
  ({ contact, balance, isFav, isBlocked, countryCode, onTap, onLongPress, onCall, onToggleFav, onWhatsApp, onSMS }) => {
    const phone = contact.phones[0]?.number;
    return (
      <View style={{ marginBottom: 8 }}>
        <SwipeRow
          onTap={() => onTap(contact.recordID)}
          onLongPress={() => onLongPress(contact.recordID)}
          leftActions={[
            { icon: "phone", color: "#16a085", onPress: () => phone && onCall(phone) },
            { icon: isFav ? "star" : "star-outline", color: "#b97d10", onPress: () => onToggleFav(contact.recordID) },
          ]}
          rightActions={[
            { icon: "whatsapp", color: "#1faa53", onPress: () => phone && onWhatsApp(phone) },
            { icon: "message-text-outline", color: "#4a7d9b", onPress: () => phone && onSMS(phone) },
          ]}
        >
          <ContactRowBody contact={contact} balance={balance} isFav={isFav} isBlocked={isBlocked} countryCode={countryCode} />
        </SwipeRow>
      </View>
    );
  }
);

const ContactRowBody: React.FC<{
  contact: DeviceContact;
  balance: number;
  isFav: boolean;
  isBlocked: boolean;
  countryCode: string;
}> = ({ contact, balance, isFav, isBlocked, countryCode }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  return (
    <View style={[styles.row, { backgroundColor: C.card, borderColor: C.line }]}>
      <Avatar contact={contact} size={46} />
      <View style={{ flex: 1, gap: 2, minWidth: 0 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
          <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink, flexShrink: 1 }} numberOfLines={1}>
            {displayName(contact)}
          </Text>
          {isFav && <Icon name="star" size={13} color="#b97d10" />}
          {isBlocked && <Pill label={t("cd.blocked")} tone="red" />}
        </View>
        <Text style={{ fontSize: F.small, color: C.sub }} numberOfLines={1}>
          {contact.phones[0]
            ? prettyPhone(contact.phones[0].number, countryCode)
            : contact.emails[0]?.address || contact.company || t("cd.noPhone")}
        </Text>
      </View>
      {balance !== 0 && <Money value={balance} colorize signed size={15} />}
      <Icon name="chevron-right" size={20} color={C.faint} />
    </View>
  );
};

/* ---------------- list items (flattened for virtualization) ---------------- */

type ListItem =
  | { kind: "letter"; key: string; letter: string }
  | { kind: "contact"; key: string; contact: DeviceContact; balance: number; isFav: boolean; isBlocked: boolean };

export const ContactsScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const contacts = useApp((s) => s.deviceContacts);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const contactX = useApp((s) => s.contactX);
  const blocked = useApp((s) => s.settings.blockedNumbers);
  const setDeviceContacts = useApp((s) => s.setDeviceContacts);
  const countryCode = useApp((s) => s.settings.countryCode);

  /* FIX: if contacts are already loaded (boot loaded them, or the user is
     returning to this tab) start as authorized — the old code stayed stuck on
     "loading" forever whenever the store already had contacts, which made the
     whole list invisible behind an eternal spinner. */
  const [perm, setPerm] = useState<PermState>(contacts.length > 0 ? "authorized" : "loading");
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<"all" | "favorites" | "theyOwe" | "youOwe" | "settled">("all");
  const [sort, setSort] = useState<SortMode>("az");
  const [menuFor, setMenuFor] = useState<string | null>(null);

  const load = useCallback(
    async (silent = false) => {
      if (!silent) setPerm("loading");
      const status = await checkContactsPermission();
      if (status === "authorized") {
        try {
          const list = await fetchAllContacts();
          setDeviceContacts(list);
          setPerm("authorized");
        } catch {
          setPerm("denied");
        }
      } else if (status === "undetermined") {
        const asked = await requestContactsPermission();
        if (asked === "authorized") {
          const list = await fetchAllContacts();
          setDeviceContacts(list);
          setPerm("authorized");
        } else {
          setPerm("denied");
        }
      } else {
        setPerm("denied");
      }
    },
    [setDeviceContacts]
  );

  useEffect(() => {
    if (contacts.length > 0) return; // already loaded at boot — nothing to do
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const favSet = useMemo(() => new Set(contactX.filter((c) => c.favorite).map((c) => c.recordID)), [contactX]);
  const blockedSet = useMemo(
    () => new Set(blocked.map((b) => normalizePhone(b, countryCode).replace(/\D/g, ""))),
    [blocked, countryCode]
  );

  const balances = useMemo(() => {
    const map = new Map<string, number>();
    entries.forEach((e) => {
      if (e.deletedAt || !e.ownerId.startsWith("c:")) return;
      const id = e.ownerId.slice(2);
      const signed = (e.type === "credit" ? 1 : -1) * (e.amount - e.payments.reduce((s, p) => s + p.amount, 0));
      map.set(id, Math.round(((map.get(id) ?? 0) + signed) * 100) / 100);
    });
    // open calculating-lists set to “post to ledger” count toward the balance
    lists.forEach((l) => {
      if (l.deletedAt || !l.ownerId.startsWith("c:") || !l.postToLedger) return;
      const id = l.ownerId.slice(2);
      const pending = listTotals(l).pending;
      map.set(id, Math.round(((map.get(id) ?? 0) + pending) * 100) / 100);
    });
    return map;
  }, [entries, lists]);

  const visible = useMemo(() => {
    let list = contacts;
    if (filter === "favorites") list = list.filter((c) => favSet.has(c.recordID));
    if (filter === "theyOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) > 0);
    if (filter === "youOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) < 0);
    if (filter === "settled") list = list.filter((c) => (balances.get(c.recordID) ?? 0) === 0);
    /* SEARCH FIX: the old code ran `.includes(queryDigits)` even when the
       query had NO digits ("" is included in everything) → every letter
       search matched every contact. Now phone matching only applies to real
       digit queries, plus T9 name matching — dialer-style. */
    const query = q.trim().toLowerCase();
    if (query) {
      const qDigits = query.replace(/\D/g, "");
      list = list.filter((c) => {
        const name = displayName(c).toLowerCase();
        if (name.includes(query)) return true;
        if (c.company?.toLowerCase().includes(query)) return true;
        if (qDigits.length >= 2) {
          if (t9Match(qDigits, name)) return true;
          if (
            c.phones.some((p) => {
              const raw = p.number.replace(/\D/g, "");
              const norm = normalizePhone(p.number, countryCode).replace(/\D/g, "");
              return raw.includes(qDigits) || norm.includes(qDigits);
            })
          )
            return true;
        }
        return false;
      });
    }
    if (sort === "balance")
      list = [...list].sort((a, b) => Math.abs(balances.get(b.recordID) ?? 0) - Math.abs(balances.get(a.recordID) ?? 0));
    else if (sort === "recent") list = [...list].sort((a, b) => Number(b.recordID || 0) - Number(a.recordID || 0));
    return list;
  }, [contacts, filter, q, sort, favSet, balances, countryCode]);

  /* summary strip */
  const owedToYou = useMemo(
    () =>
      entries
        .filter((e) => !e.deletedAt && e.ownerId.startsWith("c:") && e.type === "credit")
        .reduce((s, e) => s + (e.amount - e.payments.reduce((x, p) => x + p.amount, 0)), 0),
    [entries]
  );
  const youOwe = useMemo(
    () =>
      entries
        .filter((e) => !e.deletedAt && e.ownerId.startsWith("c:") && e.type === "debit")
        .reduce((s, e) => s + (e.amount - e.payments.reduce((x, p) => x + p.amount, 0)), 0),
    [entries]
  );

  /* flattened virtualized rows + sticky letter headers */
  const { items, sticky } = useMemo(() => {
    const out: ListItem[] = [];
    const stickyIdx: number[] = [];
    if (sort === "az") {
      let current = "";
      visible.forEach((c) => {
        let letter = (displayName(c)[0] || "#").toUpperCase();
        if (!/[A-Z]/.test(letter)) letter = "#";
        if (letter !== current) {
          current = letter;
          stickyIdx.push(out.length);
          out.push({ kind: "letter", key: `L-${letter}`, letter });
        }
        out.push({
          kind: "contact",
          key: c.recordID,
          contact: c,
          balance: balances.get(c.recordID) ?? 0,
          isFav: favSet.has(c.recordID),
          isBlocked: c.phones.some((p) => blockedSet.has(normalizePhone(p.number, countryCode).replace(/\D/g, ""))),
        });
      });
    } else {
      visible.forEach((c) =>
        out.push({
          kind: "contact",
          key: c.recordID,
          contact: c,
          balance: balances.get(c.recordID) ?? 0,
          isFav: favSet.has(c.recordID),
          isBlocked: c.phones.some((p) => blockedSet.has(normalizePhone(p.number, countryCode).replace(/\D/g, ""))),
        })
      );
    }
    return { items: out, sticky: stickyIdx };
  }, [visible, sort, balances, favSet, blockedSet, countryCode]);

  /* stable row callbacks (identity-stable → memo rows skip re-render) */
  const onTap = useCallback((recordID: string) => open({ type: "contact", recordID }), [open]);
  const onLongPress = useCallback((recordID: string) => setMenuFor(recordID), []);
  const onCall = useCallback((phone: string) => {
    placeCall(phone);
  }, []);
  const onToggleFav = useCallback((recordID: string) => useApp.getState().toggleFavorite(recordID), []);
  const onWhatsApp = useCallback(
    (phone: string) => openURL(waLink(phone, undefined, countryCode)),
    [countryCode]
  );
  const onSMS = useCallback((phone: string) => openURL(smsLink(phone)), []);

  const renderItem = useCallback(
    ({ item }: { item: ListItem }) => {
      if (item.kind === "letter") {
        return (
          <View style={[styles.letterWrap, { backgroundColor: C.canvas }]}>
            <Text style={[styles.letterHeader, { color: C.brand }]}>{item.letter}</Text>
          </View>
        );
      }
      return (
        <ContactRow
          contact={item.contact}
          balance={item.balance}
          isFav={item.isFav}
          isBlocked={item.isBlocked}
          countryCode={countryCode}
          onTap={onTap}
          onLongPress={onLongPress}
          onCall={onCall}
          onToggleFav={onToggleFav}
          onWhatsApp={onWhatsApp}
          onSMS={onSMS}
        />
      );
    },
    [C.brand, countryCode, onTap, onLongPress, onCall, onToggleFav, onWhatsApp, onSMS]
  );

  if (perm === "loading") {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <Spinner size="large" />
      </View>
    );
  }

  if (perm !== "authorized") {
    return (
      <View style={{ flex: 1, justifyContent: "center" }}>
        <EmptyState
          icon="account-multiple-outline"
          title={t("perm.contactsTitle")}
          hint={t("perm.contactsMsg")}
          actionLabel={t("st.allow")}
          onAction={() => load()}
        />
        <View style={{ alignItems: "center", marginTop: 10 }}>
          <Btn small label={t("perm.openSettings")} icon="cog-outline" variant="secondary" onPress={() => openSettings()} />
        </View>
      </View>
    );
  }

  const menuContact = contacts.find((c) => c.recordID === menuFor);

  return (
    <View style={{ flex: 1 }}>
      {/* summary strip */}
      {(owedToYou > 0 || youOwe > 0) && (
        <View style={[styles.statsRow, { borderColor: C.line }]}>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("cd.toReceive")}</Text>
            <Money value={Math.abs(owedToYou)} colorize size={17} />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("cd.toPay")}</Text>
            <Money value={-Math.abs(youOwe)} colorize size={17} />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{t("lg.net")}</Text>
            <Money value={Math.abs(owedToYou) - Math.abs(youOwe)} colorize size={17} />
          </View>
        </View>
      )}

      {/* search + filters */}
      <View style={{ paddingHorizontal: 16, paddingTop: 10 }}>
        <TextInput
          value={q}
          onChangeText={setQ}
          placeholder={t("misc.searchPlaceholder")}
          placeholderTextColor={C.faint}
          style={[styles.search, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
        />
        <View style={{ flexDirection: "row", alignItems: "center", gap: 6, marginTop: 10, flexWrap: "wrap" }}>
          <Chip label={t("act.all")} active={filter === "all"} onPress={() => setFilter("all")} />
          <Chip label={t("cd.favorites")} icon="star" active={filter === "favorites"} onPress={() => setFilter("favorites")} />
          <Chip label={t("cd.theyOweYou")} active={filter === "theyOwe"} onPress={() => setFilter("theyOwe")} />
          <Chip label={t("cd.youOweThem")} active={filter === "youOwe"} onPress={() => setFilter("youOwe")} />
          <View style={{ flex: 1 }} />
          <Pressable
            onPress={() => setSort(sort === "az" ? "balance" : sort === "balance" ? "recent" : "az")}
            style={[styles.sortBtn, { borderColor: C.line, backgroundColor: C.card }]}
          >
            <Icon
              name={sort === "az" ? "sort-alphabetical-variant" : sort === "balance" ? "scale-balance" : "clock-outline"}
              size={18}
              color={C.sub}
            />
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700" }}>
              {sort === "az" ? t("cd.sortAZ") : sort === "balance" ? t("cd.sortBalance") : t("cd.sortRecent")}
            </Text>
          </Pressable>
        </View>
      </View>

      {/* virtualized list */}
      <FlatList
        data={items}
        keyExtractor={(item) => item.key}
        renderItem={renderItem}
        stickyHeaderIndices={sticky.length ? sticky : undefined}
        keyboardShouldPersistTaps="handled"
        initialNumToRender={14}
        maxToRenderPerBatch={20}
        windowSize={9}
        removeClippedSubviews
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 20 }}
        ListEmptyComponent={
          <EmptyState
            icon="account-search-outline"
            title={q ? t("cd.noMatchingContacts") : t("cd.noContactsYet")}
            hint={q ? t("sr.tryHint") : t("cd.noContactsHint")}
          />
        }
        ListHeaderComponent={
          items.length > 0 ? (
            <Text style={{ color: C.sub, fontSize: F.small, marginBottom: 8, paddingHorizontal: 4 }}>
              {t("cd.contactsCount", { n: visible.length })}
            </Text>
          ) : null
        }
      />

      {/* long-press context sheet */}
      {menuFor && (
        <ContactMenuSheet
          onClose={() => setMenuFor(null)}
          recordID={menuFor}
          contact={menuContact ?? undefined}
        />
      )}
    </View>
  );
};

/* ---------------- long-press context sheet ---------------- */

const ContactMenuSheet: React.FC<{
  onClose: () => void;
  recordID: string;
  contact?: DeviceContact;
}> = ({ onClose, recordID, contact }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const contactX = useApp((s) => s.contactX);
  const favSet = useMemo(
    () => new Set(contactX.filter((c) => c.favorite).map((c) => c.recordID)),
    [contactX]
  );
  const toggleFavorite = useApp((s) => s.toggleFavorite);
  const blocked = useApp((s) => s.settings.blockedNumbers);
  const toggleBlocked = useApp((s) => s.toggleBlocked);
  const phone = contact?.phones[0]?.number;

  const isBlockedNow = (): boolean => {
    if (!phone) return false;
    const digits = phone.replace(/\D/g, "");
    return blocked.some((b) => b.replace(/\D/g, "") === digits);
  };

  const items: { icon: string; label: string; color?: string; run: () => void; disabled?: boolean }[] = [
    { icon: "phone", label: t("act.call"), run: () => phone && placeCallNow(phone), disabled: !phone },
    {
      icon: "whatsapp",
      label: t("act.whatsapp"),
      run: () => phone && openURL(waLink(phone, undefined, countryCode)),
      disabled: !phone,
    },
    {
      icon: "message-text-outline",
      label: t("act.sms"),
      run: () => phone && openURL(smsLink(phone)),
      disabled: !phone,
    },
    { icon: "pencil-outline", label: t("act.edit"), run: editNow },
    {
      icon: favSet.has(recordID) ? "star-off-outline" : "star",
      label: favSet.has(recordID) ? t("cd.removedFromFavorites") : t("cd.addToFavorites"),
      run: () => toggleFavorite(recordID),
    },
    {
      icon: "share-variant-outline",
      label: t("cd.shareVcard"),
      run: () => contact && shareContactVcard(contact),
      disabled: !contact,
    },
    {
      icon: "phone-off-outline",
      label: isBlockedNow() ? t("cd.unblockNumber") : t("cd.blockNumber"),
      color: C.red,
      run: () => phone && toggleBlocked(phone),
      disabled: !phone,
    },
  ];

  async function placeCallNow(number: string) {
    onClose();
    placeCall(number);
  }

  async function editNow() {
    onClose();
    openExistingContactEditor(recordID);
  }

  return (
    <BottomSheet title={contact ? displayName(contact) : t("misc.contact")} onClose={onClose}>
      <View style={{ gap: 4, paddingBottom: 10 }}>
        {items.map((it) => (
          <Pressable
            key={it.label}
            disabled={it.disabled}
            android_ripple={{ color: "rgba(128,128,128,0.12)" }}
            onPress={() => {
              onClose();
              setTimeout(it.run, 180);
            }}
            style={({ pressed }) => [
              menuStyles.item,
              { backgroundColor: pressed ? C.chip : "transparent", opacity: it.disabled ? 0.4 : 1 },
            ]}
          >
            <Icon name={it.icon} size={21} color={it.color || C.sub} />
            <Text style={{ fontSize: F.body, color: it.color || C.ink, fontWeight: "600", flexShrink: 1 }} numberOfLines={1}>
              {it.label}
            </Text>
          </Pressable>
        ))}
      </View>
    </BottomSheet>
  );
};

const menuStyles = StyleSheet.create({
  item: { flexDirection: "row", alignItems: "center", gap: 14, paddingHorizontal: 10, paddingVertical: 13, borderRadius: 12, minHeight: 52 },
});

const styles = StyleSheet.create({
  statsRow: { flexDirection: "row", gap: 8, paddingHorizontal: 16, paddingTop: 10, borderTopWidth: StyleSheet.hairlineWidth },
  statCard: { flex: 1, borderRadius: 14, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 9, gap: 2, minWidth: 0 },
  search: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, height: 46, fontSize: F.body },
  sortBtn: { flexDirection: "row", alignItems: "center", gap: 5, borderRadius: 999, borderWidth: 1, paddingHorizontal: 11, paddingVertical: 7, minHeight: 36 },
  letterWrap: { backgroundColor: "transparent", paddingVertical: 2, marginBottom: 2 },
  letterHeader: { fontSize: F.small, fontWeight: "800", marginTop: 6, marginBottom: 4, paddingHorizontal: 2 },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 11,
    minHeight: 68,
  },
});
