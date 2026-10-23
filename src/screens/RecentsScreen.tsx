/* Recents tab — the app's HOME (default on open). Android: the REAL system
   call log grouped by day with direction filters. iOS: calls started from
   callmony. FULL Contacts-tab parity (user request): the credit summary
   strip (to receive / to pay / net), balance + owes-status on every card,
   favorites strip, They-owe/You-owe chips on their own row above the list
   and the direction filters living INSIDE the sticky day headers (v1.5.3).
   Every card resolves the contact via the phone index (any local /
   international number format) and carries the SAME swipe + long-press
   actions as the Contacts tab:
   swipe right → call · favorite   swipe left → WhatsApp · SMS.
   Long-press opens the full action sheet (call / chat / copy number /
   favorite / call history / new contact). Nothing here ever blocks with a
   full-screen spinner: the disk snapshot renders instantly and the OS is
   re-read silently in the background. Rows mount in infinite-scroll pages. */

import React, { memo, useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, FlatList, Linking, Platform, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp, contactByPhone } from "../lib/store";
import { listTotals } from "../lib/calc";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { copyText, dayGroup, durationStr, fd, ft, prettyPhone, relTime } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { requestCallLogPermission } from "../native/callLog";
import { openURL, smsLink, waLink } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Chip, EmptyState, Money, toast } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

const openSettings = async () => {
  try {
    await Linking.openSettings();
  } catch {
    /* noop */
  }
};

type Filter = "all" | "missed" | "incoming" | "outgoing" | "favorites" | "theyOwe" | "youOwe";

interface CallRow {
  key: string;
  phone: string;
  name?: string;
  contact?: DeviceContact;
  direction: "incoming" | "outgoing" | "missed";
  at: number;
  duration: number;
  appId?: string;
}

/* infinite-scroll page size */
const PAGE = 60;

/* ---------------- one memoized call row ---------------- */

interface RowProps {
  row: CallRow;
  balance: number;
  countryCode: string;
  onTap: (row: CallRow) => void;
  onLongPress: (row: CallRow) => void;
  onCall: (phone: string) => void;
  onWhatsApp: (phone: string) => void;
  onSMS: (phone: string) => void;
  onToggleFav: (recordID: string) => void;
}

const CallCard: React.FC<RowProps> = memo(
  ({ row, balance, countryCode, onTap, onLongPress, onCall, onWhatsApp, onSMS, onToggleFav }) => {
    const C = useThemeColors(useApp((s) => s.settings.theme));
    const t = useT();
    const digits = row.phone.replace(/\D/g, "");
    const hasRealNumber = digits.replace(/^0+/, "").length >= 3;
    const title = row.contact
      ? row.contact.displayName || row.contact.givenName || row.contact.familyName || row.name
      : row.name || (hasRealNumber ? prettyPhone(row.phone, countryCode) : "") || t("cl.unknownNumber");
    const meta =
      row.direction === "missed"
        ? { icon: "phone-missed", color: C.red, label: t("cd.missed") }
        : row.direction === "incoming"
          ? { icon: "phone-incoming", color: C.green, label: t("cd.incoming") }
          : { icon: "phone-outgoing", color: C.brand, label: t("cd.outgoing") };
    const callNumber = row.contact?.phones[0]?.number ?? row.phone;

    /* identical swipe set to the Contacts tab (plus favorite when known) */
    const leftActions = hasRealNumber
      ? [
          { icon: "phone", color: "#16a085", onPress: () => onCall(callNumber) },
          ...(row.contact
            ? [{ icon: "star", color: "#b97d10", onPress: () => onToggleFav(row.contact!.recordID) }]
            : []),
        ]
      : [];
    const rightActions = hasRealNumber
      ? [
          { icon: "whatsapp", color: "#1faa53", onPress: () => onWhatsApp(callNumber) },
          { icon: "message-text-outline", color: "#4a7d9b", onPress: () => onSMS(callNumber) },
        ]
      : [];

    return (
      <View style={{ marginBottom: 6 }}>
        <SwipeRow
          onTap={() => onTap(row)}
          onLongPress={() => onLongPress(row)}
          leftActions={leftActions}
          rightActions={rightActions}
        >
          <View style={[styles.callRow, { backgroundColor: C.card, borderColor: C.line }]}>
            <View style={[styles.avatarRing, { borderColor: C.brandSoft }]}>
              <Avatar contact={row.contact} size={44} />
            </View>
            <View style={{ flex: 1, gap: 2, minWidth: 0 }}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
                <Text
                  style={{ fontSize: F.body, fontWeight: "700", color: row.direction === "missed" ? C.red : C.ink, flexShrink: 1 }}
                  numberOfLines={1}
                >
                  {title}
                </Text>
              </View>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
                <Icon name={meta.icon} size={13} color={meta.color} />
                <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                  {meta.label} · {ft(row.at)}
                  {row.duration > 0 ? ` · ${durationStr(row.duration)}` : ""}
                </Text>
              </View>
              {/* balance + owes-status — IN THE PLACE of the phone number line
                  (user request): the pill rides right after the in/out text;
                  the raw number only shows when there is no credit to report */}
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
                  <Money value={balance} colorize signed size={12} />
                  <Text
                    style={{ fontSize: 11, fontWeight: "700", color: balance > 0 ? C.green : C.red }}
                    numberOfLines={1}
                  >
                    · {balance > 0 ? t("cd.theyOweYou") : t("cd.youOweThem")}
                  </Text>
                </View>
              ) : (
                hasRealNumber && (
                  <Text style={{ fontSize: F.tiny, color: C.faint }} numberOfLines={1}>
                    {prettyPhone(row.phone, countryCode)}
                  </Text>
                )
              )}
            </View>
            <View style={{ alignItems: "flex-end", gap: 4 }}>
              <Text style={{ fontSize: F.tiny, color: C.faint }}>{relTime(row.at)}</Text>
              {hasRealNumber && (
                <Pressable
                  onPress={() => onCall(callNumber)}
                  hitSlop={8}
                  android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
                  style={({ pressed }) => [styles.redial, { backgroundColor: pressed ? C.brandSoft : C.cardAlt }]}
                  accessibilityLabel={t("act.call")}
                >
                  <Icon name="phone-outline" size={19} color={C.brand} />
                </Pressable>
              )}
            </View>
          </View>
        </SwipeRow>
      </View>
    );
  }
);

/* ---------------- day-header filter chips (v1.5.3) ---------------- */

/* Direction filters moved INTO the sticky day headers (user request):
   "All" stays a text chip, missed/incoming/outgoing/favorites are
   icon-only (the chip label doubles as the accessibilityLabel), all
   compact (~27px tall). The missed chip keeps its count as a tiny badge
   and turns red while unfiltered missed calls exist. Rendered inside
   EVERY day header — sticky headers keep the chips on screen while the
   list scrolls — and once more as a slim standalone row when a filter
   empties the list (no headers left to host the chips). */
const DayFilters: React.FC<{
  filter: Filter;
  onSet: (f: Filter) => void;
  missedCount: number;
}> = ({ filter, onSet, missedCount }) => {
  const t = useT();
  return (
    <View style={styles.dayFilters}>
      <Chip compact label={t("act.all")} active={filter === "all"} onPress={() => onSet("all")} />
      <Chip
        iconOnly
        icon="phone-missed"
        label={t("cd.missed")}
        count={missedCount}
        danger={filter !== "missed" && missedCount > 0}
        active={filter === "missed"}
        onPress={() => onSet("missed")}
      />
      <Chip iconOnly icon="phone-incoming" label={t("cd.incoming")} active={filter === "incoming"} onPress={() => onSet("incoming")} />
      <Chip iconOnly icon="phone-outgoing" label={t("cd.outgoing")} active={filter === "outgoing"} onPress={() => onSet("outgoing")} />
      <Chip iconOnly icon="star" label={t("cd.favorites")} active={filter === "favorites"} onPress={() => onSet("favorites")} />
    </View>
  );
};

/* ---------------- screen ---------------- */

export const RecentsScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const contacts = useApp((s) => s.deviceContacts);
  const appRecents = useApp((s) => s.recents);
  const countryCode = useApp((s) => s.settings.countryCode);
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const deleteRecent = useApp((s) => s.deleteRecent);
  const contactX = useApp((s) => s.contactX);
  const favorites = useMemo(
    () => contactX.filter((x) => x.favorite).map((x) => x.recordID),
    [contactX]
  );
  const favSet = useMemo(() => new Set(favorites), [favorites]);

  const callLogPerm = useApp((s) => s.callLogPerm);
  const systemCalls = useApp((s) => s.systemCalls);
  const [filter, setFilter] = useState<Filter>("all");
  const [refreshing, setRefreshing] = useState(false);
  const [sheetRow, setSheetRow] = useState<CallRow | null>(null);
  const [page, setPage] = useState(1);

  /* silent background refresh — NEVER a blocking full-screen spinner:
     the disk snapshot (applied during hydrate) is already on screen */
  const load = useCallback(
    async (force = false) => {
      if (Platform.OS !== "android") return;
      setRefreshing(true);
      if (force || callLogPerm !== "granted") {
        const status = await requestCallLogPermission();
        if (status !== "authorized") {
          setRefreshing(false);
          return;
        }
      }
      await useApp.getState().loadSystemCalls();
      setRefreshing(false);
    },
    [callLogPerm]
  );

  useEffect(() => {
    if (Platform.OS === "android" && callLogPerm === "unknown") load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [callLogPerm]);

  useEffect(() => setPage(1), [filter]);

  /* per-contact balances — the SAME computation as the Contacts tab
     (entries + open post-to-ledger lists), so the credit status shown on
     a Recents card always matches the Contacts row */
  const balances = useMemo(() => {
    const map = new Map<string, number>();
    entries.forEach((e) => {
      if (e.deletedAt || !e.ownerId.startsWith("c:")) return;
      const id = e.ownerId.slice(2);
      const signed = (e.type === "credit" ? 1 : -1) * (e.amount - e.payments.reduce((s, p) => s + p.amount, 0));
      map.set(id, Math.round(((map.get(id) ?? 0) + signed) * 100) / 100);
    });
    lists.forEach((l) => {
      if (l.deletedAt || !l.ownerId.startsWith("c:") || !l.postToLedger) return;
      const id = l.ownerId.slice(2);
      const pending = listTotals(l).pending;
      map.set(id, Math.round(((map.get(id) ?? 0) + pending) * 100) / 100);
    });
    return map;
  }, [entries, lists]);

  /* summary strip — identical to the Contacts tab */
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

  /* unified rows: system calls (Android) or app log (iOS) */
  const rows: CallRow[] = useMemo(() => {
    if (Platform.OS === "android") {
      return systemCalls.map((c) => ({
        key: c.id,
        phone: c.phoneNumber ?? "",
        name: c.name || undefined,
        contact: c.phoneNumber ? contactByPhone(c.phoneNumber) : undefined,
        direction: c.direction,
        at: c.timestamp,
        duration: c.duration,
      }));
    }
    return appRecents.map((r) => {
      const contact = r.ownerId?.startsWith("c:")
        ? contacts.find((c) => `c:${c.recordID}` === r.ownerId)
        : undefined;
      return {
        key: r.id,
        appId: r.id,
        phone: r.phone,
        contact,
        direction: r.direction,
        at: r.at,
        duration: r.duration,
      };
    });
  }, [systemCalls, appRecents, contacts]);

  /* direction + credit + favorites filters (Contacts-tab parity) */
  const filtered = useMemo(() => {
    if (filter === "theyOwe" || filter === "youOwe") {
      return rows.filter((r) => {
        if (!r.contact) return false;
        const b = balances.get(r.contact.recordID) ?? 0;
        return filter === "theyOwe" ? b > 0.005 : b < -0.005;
      });
    }
    if (filter === "favorites") return rows.filter((r) => !!r.contact && favSet.has(r.contact.recordID));
    return filter === "all" ? rows : rows.filter((r) => r.direction === filter);
  }, [rows, filter, balances, favSet]);
  const missedCount = useMemo(() => rows.filter((r) => r.direction === "missed").length, [rows]);

  /* flattened virtualized items + sticky day headers (+ balance per row) */
  const { items, sticky } = useMemo(() => {
    type Item =
      | { kind: "day"; key: string; label: string }
      | { kind: "call"; key: string; row: CallRow; balance: number };
    const out: Item[] = [];
    const stickyIdx: number[] = [];
    let current = "";
    filtered.forEach((r) => {
      const label = dayGroup(r.at);
      if (label !== current) {
        current = label;
        stickyIdx.push(out.length);
        out.push({ kind: "day", key: `D-${label}-${r.key}`, label });
      }
      out.push({
        kind: "call",
        key: r.key,
        row: r,
        balance: r.contact ? balances.get(r.contact.recordID) ?? 0 : 0,
      });
    });
    return { items: out, sticky: stickyIdx };
  }, [filtered, balances]);

  /* infinite scroll — feed the list in pages of PAGE rows */
  const pagedItems = useMemo(() => items.slice(0, page * PAGE), [items, page]);
  const hasMore = pagedItems.length < items.length;
  const onEndReached = useCallback(() => {
    if (hasMore) setPage((p) => p + 1);
  }, [hasMore]);

  const favContacts = useMemo(
    () =>
      favorites
        .map((id) => contacts.find((c) => c.recordID === id))
        .filter((x): x is DeviceContact => !!x),
    [favorites, contacts]
  );

  const onTap = useCallback(
    (row: CallRow) => {
      if (row.contact) open({ type: "contact", recordID: row.contact.recordID });
      else setSheetRow(row);
    },
    [open]
  );
  const onLongPress = useCallback((row: CallRow) => setSheetRow(row), []);
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
    ({ item }: { item: { kind: "day"; key: string; label: string } | { kind: "call"; key: string; row: CallRow; balance: number } }) => {
      if (item.kind === "day") {
        /* v1.5.3 — the day header IS the filter row (user request): the day
           chip on the left, flexible spacer, then the direction filters
           ("All" as text + icon-only chips). stickyHeaderIndices is
           unchanged — the chips ride along and stay pinned on screen. */
        return (
          <View style={[styles.dayWrap, { backgroundColor: C.canvas }]}>
            <View style={[styles.dayChip, { backgroundColor: C.brandSoft }]}>
              <Text style={[styles.dayHeader, { color: C.brand }]}>{item.label}</Text>
            </View>
            <View style={{ flex: 1 }} />
            <DayFilters filter={filter} onSet={setFilter} missedCount={missedCount} />
          </View>
        );
      }
      return (
        <CallCard
          row={item.row}
          balance={item.balance}
          countryCode={countryCode}
          onTap={onTap}
          onLongPress={onLongPress}
          onCall={onCall}
          onWhatsApp={onWhatsApp}
          onSMS={onSMS}
          onToggleFav={onToggleFav}
        />
      );
    },
    [C.brand, C.brandSoft, C.canvas, filter, missedCount, countryCode, onTap, onLongPress, onCall, onToggleFav, onWhatsApp, onSMS]
  );

  /* permission denied → the request card (never a blocking spinner) */
  if (Platform.OS === "android" && callLogPerm === "denied") {
    return (
      <View style={{ flex: 1, justifyContent: "center" }}>
        <EmptyState
          icon="phone-missed"
          title={t("perm.callLogTitle")}
          hint={t("perm.callLogMsg")}
          actionLabel={t("st.allow")}
          onAction={() => load(true)}
        />
        <View style={{ alignItems: "center", marginTop: 10 }}>
          <Btn small label={t("perm.openSettings")} icon="cog-outline" variant="secondary" onPress={() => openSettings()} />
        </View>
      </View>
    );
  }

  return (
    <View style={{ flex: 1 }}>
      {/* credit summary strip — v1.5.3 MUCH tighter (user request): three
          slim cards — bare 10px icons (no dot tiles), 10px labels, 12px
          money, ~1px gaps — roughly half the old vertical budget. Net keeps
          the brand-soft tint so it still reads as the headline number. */}
      {(owedToYou > 0.005 || youOwe > 0.005) && (
        <View style={[styles.statsRow, { borderColor: C.line }]}>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <View style={styles.statLabel}>
              <Icon name="arrow-bottom-left" size={10} color={C.green} />
              <Text style={[styles.statLabelTxt, { color: C.sub }]} numberOfLines={1}>{t("cd.toReceive")}</Text>
            </View>
            <Money value={Math.abs(owedToYou)} colorize size={12} weight="800" />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.card, borderColor: C.line }]}>
            <View style={styles.statLabel}>
              <Icon name="arrow-top-right" size={10} color={C.red} />
              <Text style={[styles.statLabelTxt, { color: C.sub }]} numberOfLines={1}>{t("cd.toPay")}</Text>
            </View>
            <Money value={-Math.abs(youOwe)} colorize size={12} weight="800" />
          </View>
          <View style={[styles.statCard, { backgroundColor: C.brandSoft, borderColor: C.brandSoft }]}>
            <View style={styles.statLabel}>
              <Icon name="scale-balance" size={10} color={C.brand} />
              <Text style={[styles.statLabelTxt, { color: C.brand, fontWeight: "700" }]} numberOfLines={1}>{t("lg.net")}</Text>
            </View>
            <Money value={Math.abs(owedToYou) - Math.abs(youOwe)} colorize size={12} weight="800" />
          </View>
        </View>
      )}

      {/* favorites strip */}
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

      {/* credit filters — the ONLY chip row left above the list (v1.5.3):
          They owe / You owe on a row of their own; the direction chips now
          live inside the sticky day headers further down */}
      <View style={styles.oweRow}>
        <Chip compact icon="arrow-bottom-left" label={t("cd.theyOweYou")} active={filter === "theyOwe"} onPress={() => setFilter("theyOwe")} />
        <Chip compact icon="arrow-top-right" label={t("cd.youOweThem")} active={filter === "youOwe"} onPress={() => setFilter("youOwe")} />
      </View>

      {/* a filter that empties the list leaves NO day headers to host the
          direction chips — this slim standalone row keeps them reachable */}
      {items.length === 0 && (
        <View style={styles.fallbackRow}>
          <DayFilters filter={filter} onSet={setFilter} missedCount={missedCount} />
        </View>
      )}

      {/* virtualized call list with infinite scroll.
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
          callLogPerm === "unknown" && systemCalls.length === 0 ? (
            /* first paint before the (background) permission/log read finishes —
               an inline loader, the app stays fully usable */
            <View style={{ alignItems: "center", justifyContent: "center", paddingTop: 80, gap: 12 }}>
              <ActivityIndicator size="large" color={C.brand} />
              <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "600" }}>{t("cd.loadingCalls")}</Text>
            </View>
          ) : (
            <EmptyState
              icon="history"
              title={filter !== "all" ? t("cd.noMatchingContacts") : t("cd.noCallsYet")}
              hint={filter !== "all" ? t("cd.tryAnotherFilter") : t("cd.noCallsHint")}
            />
          )
        }
        ListHeaderComponent={
          Platform.OS !== "android" && items.length > 0 ? (
            <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 8, lineHeight: 18 }}>
              {t("cl.appCallsHint")}
            </Text>
          ) : null
        }
        refreshing={refreshing}
        onRefresh={() => load(true)}
      />

      {/* per-row action sheet — full Contacts parity (long-press or unknown tap) */}
      {sheetRow && (
        <CallRowSheet
          row={sheetRow}
          countryCode={countryCode}
          onClose={() => setSheetRow(null)}
          onDelete={sheetRow.appId ? () => deleteRecent(sheetRow.appId!) : undefined}
        />
      )}
    </View>
  );
};

/* ---------------- per-row actions (known + unknown numbers) ---------------- */

const CallRowSheet: React.FC<{
  row: CallRow;
  countryCode: string;
  onClose: () => void;
  onDelete?: () => void;
}> = ({ row, countryCode, onClose, onDelete }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const contactX = useApp((s) => s.contactX);
  const toggleFavorite = useApp((s) => s.toggleFavorite);
  const digits = row.phone.replace(/\D/g, "");
  const hasRealNumber = digits.replace(/^0+/, "").length >= 3;
  const callNumber = row.contact?.phones[0]?.number ?? row.phone;
  const isFav = !!row.contact && contactX.some((x) => x.recordID === row.contact!.recordID && x.favorite);

  const items: { icon: string; label: string; color?: string; run: () => void; disabled?: boolean }[] = [
    { icon: "phone", label: t("act.call"), run: () => hasRealNumber && placeCall(callNumber), disabled: !hasRealNumber },
    {
      icon: "whatsapp",
      label: t("act.whatsapp"),
      run: () => hasRealNumber && openURL(waLink(callNumber, undefined, countryCode)),
      disabled: !hasRealNumber,
    },
    {
      icon: "message-text-outline",
      label: t("act.sms"),
      run: () => hasRealNumber && openURL(smsLink(callNumber)),
      disabled: !hasRealNumber,
    },
    {
      icon: "content-copy",
      label: t("cd.copyNumber"),
      run: () =>
        hasRealNumber &&
        copyText(row.phone).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error")),
      disabled: !hasRealNumber,
    },
    ...(row.contact
      ? [
          {
            icon: isFav ? "star-off-outline" : "star",
            label: isFav ? t("cd.removedFromFavorites") : t("cd.addToFavorites"),
            run: () => toggleFavorite(row.contact!.recordID),
          } as const,
          {
            icon: "account-circle-outline",
            label: t("cd.callHistory"),
            run: () => {
              onClose();
              useUI.getState().open({ type: "contact", recordID: row.contact!.recordID, initialSeg: "info" });
            },
          } as const,
        ]
      : [
          {
            icon: "account-plus-outline",
            label: t("act.newContact"),
            run: async () => {
              const p = row.phone;
              onClose();
              const saved = await openNewContactForm({ phone: p });
              if (saved) {
                toast(t("ce.contactSaved"), "ok");
                useApp.getState().refreshDeviceContacts(); // show it at once
              }
            },
          } as const,
        ]),
    ...(onDelete
      ? [
          {
            icon: "trash-can-outline",
            label: t("cd.deleteLog"),
            color: C.red,
            run: () => {
              onDelete();
              onClose();
              toast(t("cd.logDeleted"), "ok");
            },
          } as const,
        ]
      : []),
  ];

  const title = row.contact
    ? row.contact.displayName || row.contact.givenName || t("misc.contact")
    : hasRealNumber
      ? prettyPhone(row.phone, countryCode)
      : t("cl.unknownNumber");

  return (
    <BottomSheet title={title} onClose={onClose}>
      <Text style={{ fontSize: F.small, color: C.sub, marginTop: 2, marginBottom: 4 }}>
        {fd(row.at)} · {ft(row.at)}
        {row.duration > 0 ? ` · ${durationStr(row.duration)}` : ""}
      </Text>
      <View style={{ gap: 4, marginTop: 12, paddingBottom: 8 }}>
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
              rowStyles.item,
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

const rowStyles = StyleSheet.create({
  item: { flexDirection: "row", alignItems: "center", gap: 14, paddingHorizontal: 10, paddingVertical: 13, borderRadius: 12, minHeight: 52 },
});

const styles = StyleSheet.create({
  statsRow: { flexDirection: "row", gap: 4, paddingHorizontal: 16, paddingTop: 4, borderTopWidth: StyleSheet.hairlineWidth },
  statCard: { flex: 1, borderRadius: 10, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 3, gap: 1, minWidth: 0 },
  statLabel: { flexDirection: "row", alignItems: "center", gap: 3 },
  statLabelTxt: { fontSize: 10, lineHeight: 12, fontWeight: "600" },
  oweRow: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 16, paddingTop: 6, paddingBottom: 3 },
  fallbackRow: { flexDirection: "row", alignItems: "center", gap: 4, paddingHorizontal: 16, paddingBottom: 4 },
  dayWrap: { flexDirection: "row", alignItems: "center", gap: 6, paddingTop: 6, paddingBottom: 3, backgroundColor: "transparent" },
  dayChip: { borderRadius: 8, paddingHorizontal: 9, paddingVertical: 2 },
  dayFilters: { flexDirection: "row", alignItems: "center", gap: 4 },
  dayHeader: { fontSize: F.small, fontWeight: "800" },
  callRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 20,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    minHeight: 68,
  },
  avatarRing: { borderRadius: 26, borderWidth: 2, padding: 1 },
  favRing: { borderRadius: 31, borderWidth: 2, padding: 1 },
  redial: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  balancePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 2,
    borderRadius: 999,
    paddingHorizontal: 7,
    paddingVertical: 3,
    maxWidth: 118,
  },
});
