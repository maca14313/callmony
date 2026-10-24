/* Contacts tab — every phone contact in a VIRTUALIZED FlatList.
   FETCH POLICY (user request): the OS address book is queried ONLY when
   this tab is opened (mount) AND the disk snapshot is stale/empty — the
   boot sequence never touches it. The sync is SILENT: the cached list
   stays fully interactive while it refreshes (no blocking spinner).
   Rows are memoized; only the visible rows are mounted at a time, and an
   infinite-scroll pager feeds them in 80-row pages as you scroll.
   Sticky A–Z section headers, swipe actions, favorites strip, balances.
   Search lives ONLY in the global SearchOverlay (header magnify icon) —
   no second search input here, exactly like the webapp. */

import React, { memo, useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, FlatList, Linking, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { listTotals } from "../lib/calc";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import {
  CONTACTS_TTL,
  checkContactsPermission,
  openExistingContactEditor,
  requestContactsPermission,
} from "../native/deviceContacts";
import { placeCall } from "../native/calls";
import { openURL, smsLink, waLink } from "../lib/share";
import { shareContactVcard } from "../lib/exporters";
import { copyText } from "../lib/utils";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Chip, EmptyState, Money, Pill, toast } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

type SortMode = "az" | "balance" | "recent";
type PermState = "idle" | "authorized" | "denied";

const openSettings = async () => {
  try {
    await Linking.openSettings();
  } catch {
    /* noop */
  }
};

/* infinite-scroll page size — rows mount in pages of 80 as you scroll */
const PAGE = 80;

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
      {/* avatar with a soft brand ring — pops on the card */}
      <View style={[styles.avatarRing, { borderColor: C.brandSoft }]}>
        <Avatar contact={contact} size={46} />
      </View>
      <View style={{ flex: 1, gap: 2, minWidth: 0 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
          <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink, flexShrink: 1 }} numberOfLines={1}>
            {displayName(contact)}
          </Text>
          {isFav && <Icon name="star" size={13} color="#b97d10" />}
          {isBlocked && <Pill label={t("cd.blocked")} tone="red" />}
        </View>
        {/* balance + owes-status — IN THE PLACE of the phone-number line
            (user request); the number only shows when there is no credit */}
        {balance !== 0 ? (
          <View
            style={[
              styles.balancePill,
              { backgroundColor: balance > 0 ? "rgba(22,160,133,0.13)" : "rgba(214,69,69,0.11)", alignSelf: "flex-start" },
            ]}
          >
            <Icon
              name={balance > 0 ? "arrow-bottom-left" : "arrow-top-right"}
              size={11}
              color={balance > 0 ? C.green : C.red}
            />
            <Money value={balance} colorize signed size={12} compact />
            <Text style={{ fontSize: 11, fontWeight: "700", color: balance > 0 ? C.green : C.red }} numberOfLines={1}>
              · {balance > 0 ? t("cd.theyOweYou") : t("cd.youOweThem")}
            </Text>
          </View>
        ) : (
          <Text style={{ fontSize: F.small, color: C.sub }} numberOfLines={1}>
            {contact.phones[0]
              ? prettyPhone(contact.phones[0].number, countryCode)
              : contact.emails[0]?.address || contact.company || t("cd.noPhone")}
          </Text>
        )}
      </View>
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
  const countryCode = useApp((s) => s.settings.countryCode);

  const [perm, setPerm] = useState<PermState>(contacts.length > 0 ? "authorized" : "idle");
  const [syncing, setSyncing] = useState(false);
  const [filter, setFilter] = useState<"all" | "favorites" | "theyOwe" | "youOwe" | "settled">("all");
  const [sort, setSort] = useState<SortMode>("az");
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [page, setPage] = useState(1);

  /* FETCH ON TAB OPEN — this screen mounts exactly when the user taps the
     Contacts tab. The cached list (applied at boot from the disk snapshot)
     renders instantly; the OS is queried only when that snapshot is empty
     (first run) or older than CONTACTS_TTL. The sync never blocks the UI. */
  useEffect(() => {
    let cancelled = false;
    (async () => {
      let status = await checkContactsPermission();
      if (status === "undetermined") status = await requestContactsPermission();
      if (cancelled) return;
      if (status !== "authorized") {
        setPerm("denied");
        return;
      }
      setPerm("authorized");
      const { deviceContacts, contactsLoadedAt } = useApp.getState();
      if (!deviceContacts.length || Date.now() - contactsLoadedAt >= CONTACTS_TTL) {
        setSyncing(true);
        await useApp.getState().refreshDeviceContacts();
        if (!cancelled) setSyncing(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* restart the infinite scroll when the lens changes */
  useEffect(() => setPage(1), [filter, sort]);

  const favSet = useMemo(() => new Set(contactX.filter((c) => c.favorite).map((c) => c.recordID)), [contactX]);
  const blockedSet = useMemo(
    () => new Set(blocked.map((b) => normalizePhone(b, countryCode).replace(/\D/g, ""))),
    [blocked, countryCode]
  );

  /* favorites strip — same as the webapp Recents strip */
  const favContacts = useMemo(
    () =>
      favContactsOf(contacts, favSet).sort((a, b) => displayName(a).localeCompare(displayName(b))),
    [contacts, favSet]
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
    if (filter === "theyOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) > 0.005);
    if (filter === "youOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) < -0.005);
    if (filter === "settled") list = list.filter((c) => Math.abs(balances.get(c.recordID) ?? 0) <= 0.005);
    if (sort === "balance")
      list = [...list].sort((a, b) => Math.abs(balances.get(b.recordID) ?? 0) - Math.abs(balances.get(a.recordID) ?? 0));
    else if (sort === "recent") list = [...list].sort((a, b) => Number(b.recordID || 0) - Number(a.recordID || 0));
    return list;
  }, [contacts, filter, sort, favSet, balances]);

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

  /* infinite scroll — feed the list in pages of PAGE rows */
  const pagedItems = useMemo(() => items.slice(0, page * PAGE), [items, page]);
  const hasMore = pagedItems.length < items.length;
  const onEndReached = useCallback(() => {
    if (hasMore) setPage((p) => p + 1);
  }, [hasMore]);

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
            <View style={[styles.letterChip, { backgroundColor: C.brandSoft }]}>
              <Text style={[styles.letterHeader, { color: C.brand }]}>{item.letter}</Text>
            </View>
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
    [C.brand, C.brandSoft, C.canvas, countryCode, onTap, onLongPress, onCall, onToggleFav, onWhatsApp, onSMS]
  );

  /* v1.6.8 — OUR OWN in-app creator (never the OS contact app) */
  const newContact = useCallback(() => {
    open({ type: "contactNew" });
  }, [open]);

  /* permission denied → the request card (never a blocking spinner) */
  if (perm === "denied") {
    return (
      <View style={{ flex: 1, justifyContent: "center" }}>
        <EmptyState
          icon="account-multiple-outline"
          title={t("perm.contactsTitle")}
          hint={t("perm.contactsMsg")}
          actionLabel={t("st.allow")}
          onAction={async () => {
            const status = await requestContactsPermission();
            if (status === "authorized") setPerm("authorized");
          }}
        />
        <View style={{ alignItems: "center", marginTop: 10 }}>
          <Btn small label={t("perm.openSettings")} icon="cog-outline" variant="secondary" onPress={() => openSettings()} />
        </View>
      </View>
    );
  }

  /* first run, nothing cached yet → an inline loading STATE inside the
     content area (header + bottom nav stay fully usable — never a
     full-screen spinner that makes the app unusable) */
  if (perm === "idle" && contacts.length === 0) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: 12, paddingHorizontal: 40 }}>
        <ActivityIndicator size="large" color={C.brand} />
        <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "600" }}>{t("cd.loadingContacts")}</Text>
      </View>
    );
  }

  const menuContact = contacts.find((c) => c.recordID === menuFor);

  return (
    <View style={{ flex: 1 }}>
      {/* summary strip — v1.5.3 MUCH tighter (user request), same metrics as
          the Recents tab: bare 10px icons (no dot tiles), 10px labels, 12px
          money, ~1px gaps — roughly half the old vertical budget; net keeps
          the brand-soft tint so it still reads as the headline number */}
      {(owedToYou > 0.005 || youOwe > 0.005) && (
        <View style={[styles.statsRow, { borderColor: C.line }]}>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <View style={styles.statLabel}>
              <Icon name="arrow-bottom-left" size={10} color={C.green} />
              <Text style={[styles.statLabelTxt, { color: C.sub }]} numberOfLines={1}>{t("cd.toReceive")}</Text>
            </View>
            <Money value={Math.abs(owedToYou)} colorize size={12} weight="800" compact />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <View style={styles.statLabel}>
              <Icon name="arrow-top-right" size={10} color={C.red} />
              <Text style={[styles.statLabelTxt, { color: C.sub }]} numberOfLines={1}>{t("cd.toPay")}</Text>
            </View>
            <Money value={-Math.abs(youOwe)} colorize size={12} weight="800" compact />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.brandSoft, borderColor: C.brandSoft }]}>
            <View style={styles.statLabel}>
              <Icon name="scale-balance" size={10} color={C.brand} />
              <Text style={[styles.statLabelTxt, { color: C.brand, fontWeight: "700" }]} numberOfLines={1}>{t("lg.net")}</Text>
            </View>
            <Money value={Math.abs(owedToYou) - Math.abs(youOwe)} colorize size={12} weight="800" compact />
          </View>
        </View>
      )}

      {/* favorites strip — one tap opens the contact */}
      {favContacts.length > 0 && (
        <View style={{ paddingTop: 10, paddingBottom: 2 }}>
          <ScrollView
            horizontal
            contentContainerStyle={{ paddingHorizontal: 16, gap: 14, alignItems: "center", paddingBottom: 4 }}
            showsHorizontalScrollIndicator={false}
          >
            {favContacts.map((c) => (
              <Pressable
                key={c.recordID}
                onPress={() => open({ type: "contact", recordID: c.recordID })}
                style={{ alignItems: "center", width: 62 }}
              >
                <View style={[styles.favRing, { borderColor: C.brandSoft }]}>
                  <Avatar contact={c} size={52} />
                </View>
                <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 4 }} numberOfLines={1}>
                  {(c.givenName || c.displayName || "?").split(" ")[0]}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      )}

      {/* filter chips — v1.5.3 SPLIT into two compact rows (user request):
          They owe / You owe get a row of their own; All stays text while
          Settled and Favorites are icon-only (label = accessibilityLabel) */}
      <View style={styles.filterRow}>
        <Chip compact icon="arrow-bottom-left" label={t("cd.theyOweYou")} active={filter === "theyOwe"} onPress={() => setFilter("theyOwe")} />
        <Chip compact icon="arrow-top-right" label={t("cd.youOweThem")} active={filter === "youOwe"} onPress={() => setFilter("youOwe")} />
      </View>
      <View style={[styles.filterRow, { paddingBottom: 6 }]}>
        <Chip compact label={t("act.all")} active={filter === "all"} onPress={() => setFilter("all")} />
        <Chip iconOnly icon="check-circle-outline" label={t("cd.settledUp")} active={filter === "settled"} onPress={() => setFilter("settled")} />
        <Chip iconOnly icon="star" label={t("cd.favorites")} active={filter === "favorites"} onPress={() => setFilter("favorites")} />
      </View>

      {/* count + sort + new contact + silent-sync indicator */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 16, paddingTop: 10, paddingBottom: 4 }}>
        <Text style={{ color: C.sub, fontSize: F.small, flex: 1 }}>
          {visible.length === contacts.length
            ? t("cd.contactsCount", { n: contacts.length })
            : `${visible.length} / ${contacts.length}`}
        </Text>
        {syncing && <ActivityIndicator size="small" color={C.brand} />}
        <Pressable
          onPress={() => setSort(sort === "az" ? "balance" : sort === "balance" ? "recent" : "az")}
          style={[styles.sortBtn, { borderColor: C.line, backgroundColor: C.card }]}
        >
          <Icon
            name={sort === "az" ? "sort-alphabetical-variant" : sort === "balance" ? "scale-balance" : "clock-outline"}
            size={16}
            color={C.brand}
          />
          <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700" }}>
            {sort === "az" ? t("cd.sortAZ") : sort === "balance" ? t("cd.sortBalance") : t("cd.sortRecent")}
          </Text>
        </Pressable>
        <Pressable
          onPress={newContact}
          style={[styles.newBtn, { backgroundColor: C.brandSoft }]}
          accessibilityLabel={t("act.newContact")}
        >
          {/* v1.6.10 — the create-CONTACT glyph everywhere the action lives
              (same account-plus icon as the Recents day headers) */}
          <Icon name="account-plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.tiny, color: C.brand, fontWeight: "800" }}>{t("act.newContact")}</Text>
        </Pressable>
      </View>

      {/* virtualized list with infinite scroll.
          removeClippedSubviews MUST stay false — Android defaults it to true,
          and a data swap while cells are clipped makes manageChildren insert
          into a native parent with 0 attached children → IndexOutOfBoundsException
          (the v1.5.2 boot crash, stack: ReactClippingViewManager.addView). */}
      <FlatList
        data={pagedItems}
        keyExtractor={(item) => item.key}
        renderItem={renderItem}
        stickyHeaderIndices={sticky.length ? sticky : undefined}
        keyboardShouldPersistTaps="handled"
        initialNumToRender={12}
        maxToRenderPerBatch={12}
        windowSize={7}
        removeClippedSubviews={false}
        onEndReached={onEndReached}
        onEndReachedThreshold={3}
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 8, paddingBottom: 28 }}
        ListFooterComponent={
          hasMore ? (
            <View style={{ paddingVertical: 14, alignItems: "center" }}>
              <ActivityIndicator size="small" color={C.brand} />
            </View>
          ) : null
        }
        ListEmptyComponent={
          <EmptyState
            icon="account-search-outline"
            title={filter !== "all" ? t("cd.noMatchingContacts") : t("cd.noContactsYet")}
            hint={filter !== "all" ? t("cd.tryAnotherFilter") : t("cd.noContactsHint")}
          />
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

/** favorites resolved against the loaded device contacts */
const favContactsOf = (contacts: DeviceContact[], favSet: Set<string>): DeviceContact[] =>
  contacts.filter((c) => favSet.has(c.recordID));

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
    { icon: "pencil-outline", label: t("cd.editContact"), run: editNow },
    {
      icon: favSet.has(recordID) ? "star-off-outline" : "star",
      label: favSet.has(recordID) ? t("cd.removedFromFavorites") : t("cd.addToFavorites"),
      run: () => toggleFavorite(recordID),
    },
    {
      icon: "content-copy",
      label: t("cd.copyNumber"),
      run: () =>
        phone &&
        copyText(phone).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error")),
      disabled: !phone,
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
    const ok = await openExistingContactEditor(recordID);
    if (ok) useApp.getState().refreshDeviceContacts(); // pick up OS edits at once
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
  statsRow: { flexDirection: "row", gap: 4, paddingHorizontal: 16, paddingTop: 4, borderTopWidth: StyleSheet.hairlineWidth },
  statCard: { flex: 1, borderRadius: 10, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, gap: 1, minWidth: 0 },
  statLabel: { flexDirection: "row", alignItems: "center", gap: 3 },
  statLabelTxt: { fontSize: 10, lineHeight: 12, fontWeight: "600" },
  filterRow: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 16, paddingTop: 8, paddingBottom: 2 },
  sortBtn: { flexDirection: "row", alignItems: "center", gap: 5, borderRadius: 999, borderWidth: 1, paddingHorizontal: 11, paddingVertical: 7, minHeight: 36 },
  newBtn: { flexDirection: "row", alignItems: "center", gap: 4, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 8, minHeight: 36 },
  letterWrap: { backgroundColor: "transparent", paddingVertical: 2, marginBottom: 4 },
  letterChip: { alignSelf: "flex-start", borderRadius: 8, paddingHorizontal: 9, paddingVertical: 2 },
  letterHeader: { fontSize: F.small, fontWeight: "800" },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 11,
    minHeight: 70,
  },
  avatarRing: { borderRadius: 27, borderWidth: 2, padding: 1 },
  favRing: { borderRadius: 31, borderWidth: 2, padding: 1 },
  balancePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
    borderRadius: 999,
    paddingHorizontal: 9,
    paddingVertical: 5,
    maxWidth: 130,
  },
});
