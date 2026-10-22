/* Recents tab — Android: the REAL system call log grouped by day with
   direction filters (single-row horizontal scroll chips). iOS: calls
   started from callmony. Rows are virtualized and memoized (400+ calls
   scroll smoothly), every card resolves the contact via the phone index
   (any local/international number format) and carries the SAME swipe +
   long-press actions as the Contacts tab:
   swipe right → call · favorite   swipe left → WhatsApp · SMS.
   Long-press opens the full action sheet (call / chat / copy number /
   favorite / call history / new contact). */

import React, { memo, useCallback, useEffect, useMemo, useState } from "react";
import { FlatList, Linking, Platform, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp, contactByPhone } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { copyText, dayGroup, durationStr, fd, ft, prettyPhone, relTime } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { requestCallLogPermission } from "../native/callLog";
import { openURL, smsLink, waLink } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Chip, EmptyState, Spinner, toast } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

const openSettings = async () => {
  try {
    await Linking.openSettings();
  } catch {
    /* noop */
  }
};

type Filter = "all" | "missed" | "incoming" | "outgoing";

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

/* ---------------- one memoized call row ---------------- */

interface RowProps {
  row: CallRow;
  countryCode: string;
  onTap: (row: CallRow) => void;
  onLongPress: (row: CallRow) => void;
  onCall: (phone: string) => void;
  onWhatsApp: (phone: string) => void;
  onSMS: (phone: string) => void;
  onToggleFav: (recordID: string) => void;
}

const CallCard: React.FC<RowProps> = memo(
  ({ row, countryCode, onTap, onLongPress, onCall, onWhatsApp, onSMS, onToggleFav }) => {
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
            <Avatar contact={row.contact} size={44} />
            <View style={{ flex: 1, gap: 2, minWidth: 0 }}>
              <Text
                style={{ fontSize: F.body, fontWeight: "700", color: row.direction === "missed" ? C.red : C.ink, flexShrink: 1 }}
                numberOfLines={1}
              >
                {title}
              </Text>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
                <Icon name={meta.icon} size={14} color={meta.color} />
                <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                  {meta.label} · {ft(row.at)}
                  {row.duration > 0 ? ` · ${durationStr(row.duration)}` : ""}
                </Text>
              </View>
              {/* the matched number — so the card always carries real info */}
              {hasRealNumber && (
                <Text style={{ fontSize: F.tiny, color: C.faint }} numberOfLines={1}>
                  {prettyPhone(row.phone, countryCode)}
                </Text>
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
                  <Icon name="phone-outline" size={20} color={C.brand} />
                </Pressable>
              )}
            </View>
          </View>
        </SwipeRow>
      </View>
    );
  }
);

/* ---------------- screen ---------------- */

export const RecentsScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const contacts = useApp((s) => s.deviceContacts);
  const appRecents = useApp((s) => s.recents);
  const countryCode = useApp((s) => s.settings.countryCode);
  const deleteRecent = useApp((s) => s.deleteRecent);
  const contactX = useApp((s) => s.contactX);
  const favorites = useMemo(
    () => contactX.filter((x) => x.favorite).map((x) => x.recordID),
    [contactX]
  );

  const callLogPerm = useApp((s) => s.callLogPerm);
  const systemCalls = useApp((s) => s.systemCalls);
  const [filter, setFilter] = useState<Filter>("all");
  const [refreshing, setRefreshing] = useState(false);
  const [sheetRow, setSheetRow] = useState<CallRow | null>(null);

  const perm: "loading" | "ok" | "denied" =
    Platform.OS !== "android"
      ? "ok"
      : callLogPerm === "granted"
        ? "ok"
        : callLogPerm === "denied"
          ? "denied"
          : "loading";

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

  const filtered = useMemo(
    () => (filter === "all" ? rows : rows.filter((r) => r.direction === filter)),
    [rows, filter]
  );
  const missedCount = useMemo(() => rows.filter((r) => r.direction === "missed").length, [rows]);

  /* flattened virtualized items + sticky day headers */
  const { items, sticky } = useMemo(() => {
    type Item =
      | { kind: "day"; key: string; label: string }
      | { kind: "call"; key: string; row: CallRow };
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
      out.push({ kind: "call", key: r.key, row: r });
    });
    return { items: out, sticky: stickyIdx };
  }, [filtered]);

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
    ({ item }: { item: { kind: "day"; key: string; label: string } | { kind: "call"; key: string; row: CallRow } }) => {
      if (item.kind === "day") {
        return (
          <View style={[styles.dayWrap, { backgroundColor: C.canvas }]}>
            <Text style={[styles.dayHeader, { color: C.brand }]}>{item.label}</Text>
          </View>
        );
      }
      return (
        <CallCard
          row={item.row}
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
    [C.brand, C.canvas, countryCode, onTap, onLongPress, onCall, onToggleFav, onWhatsApp, onSMS]
  );

  if (perm === "loading") {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center" }}>
        <Spinner size="large" />
      </View>
    );
  }

  if (Platform.OS === "android" && perm === "denied") {
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
      {/* favorites strip */}
      {favContacts.length > 0 && (
        <View style={{ paddingTop: 10 }}>
          <ScrollView
            horizontal
            contentContainerStyle={{ paddingHorizontal: 16, gap: 14 }}
            showsHorizontalScrollIndicator={false}
          >
            {favContacts.map((c) => (
              <Pressable
                key={c.recordID}
                onPress={() => open({ type: "contact", recordID: c.recordID })}
                style={{ alignItems: "center", width: 62 }}
              >
                <Avatar contact={c} size={52} />
                <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 4 }} numberOfLines={1}>
                  {(c.givenName || c.displayName || "?").split(" ")[0]}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      )}

      {/* direction chips — single-row horizontal scroll, compact (webapp style) */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ paddingHorizontal: 16, gap: 6, paddingTop: 10 }}
        style={{ flexGrow: 0 }}
      >
        <Chip label={t("act.all")} active={filter === "all"} onPress={() => setFilter("all")} />
        <Chip
          label={missedCount > 0 ? `${t("cd.missed")} · ${missedCount}` : t("cd.missed")}
          danger={!filter.startsWith("missed") && missedCount > 0}
          active={filter === "missed"}
          onPress={() => setFilter("missed")}
        />
        <Chip label={t("cd.incoming")} active={filter === "incoming"} onPress={() => setFilter("incoming")} />
        <Chip label={t("cd.outgoing")} active={filter === "outgoing"} onPress={() => setFilter("outgoing")} />
      </ScrollView>

      {/* virtualized call list */}
      <FlatList
        data={items}
        keyExtractor={(item) => item.key}
        renderItem={renderItem}
        stickyHeaderIndices={sticky.length ? sticky : undefined}
        keyboardShouldPersistTaps="handled"
        initialNumToRender={10}
        maxToRenderPerBatch={10}
        windowSize={5}
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 8, paddingBottom: 20 }}
        ListEmptyComponent={
          <EmptyState
            icon="history"
            title={t("cd.noCallsYet")}
            hint={t("cd.noCallsHint")}
          />
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
              if (saved) toast(t("ce.contactSaved"), "ok");
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
  dayWrap: { backgroundColor: "transparent", paddingBottom: 2 },
  dayHeader: { fontSize: F.small, fontWeight: "800", marginTop: 8, marginBottom: 4, paddingHorizontal: 2 },
  callRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 13,
    paddingVertical: 10,
    minHeight: 66,
  },
  redial: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
});
