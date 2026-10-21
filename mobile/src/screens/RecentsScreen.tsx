/* Recents tab — Android: the real system call log (react-native-call-log),
   grouped by day with direction filters. iOS: calls started from callmony
   (iOS does not expose the system call log to apps). */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { Platform } from "react-native";
import { useApp, contactByPhone } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dayGroup, durationStr, fd, ft, copyText, prettyPhone, relTime } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { fetchSystemCalls, requestCallLogPermission, type DeviceCall } from "../native/callLog";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Chip, EmptyState, Money, Spinner, toast } from "../components/kit";

type Filter = "all" | "missed" | "incoming" | "outgoing";

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

  const [systemCalls, setSystemCalls] = useState<DeviceCall[] | null>(null);
  const [perm, setPerm] = useState<"loading" | "ok" | "denied">("loading");
  const [filter, setFilter] = useState<Filter>("all");
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async (force = false) => {
    if (Platform.OS === "android") {
      setRefreshing(true);
      const status = force || perm === "loading" ? await requestCallLogPermission() : perm === "ok" ? "authorized" : "denied";
      if (status === "authorized") {
        try {
          const calls = await fetchSystemCalls(400);
          setSystemCalls(calls);
          setPerm("ok");
        } catch {
          setPerm("denied");
        }
      } else {
        setPerm("denied");
      }
      setRefreshing(false);
    } else {
      setPerm("ok"); // iOS uses the in-app log
      setSystemCalls([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [perm]);

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* unified rows: system calls (Android) or app log (iOS) */
  interface Row {
    key: string;
    phone: string;
    name?: string;
    contact?: DeviceContact;
    direction: "incoming" | "outgoing" | "missed";
    at: number;
    duration: number;
    appId?: string;
  }

  const rows: Row[] = useMemo(() => {
    if (Platform.OS === "android" && systemCalls) {
      return systemCalls.map((c) => {
        const contact = c.phoneNumber ? contactByPhone(c.phoneNumber) : undefined;
        return {
          key: c.id,
          phone: c.phoneNumber,
          name: c.name || undefined,
          contact,
          direction: c.direction,
          at: c.timestamp,
          duration: c.duration,
        };
      });
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
        name: contact ? undefined : prettyPhone(r.phone, countryCode),
        direction: r.direction,
        at: r.at,
        duration: r.duration,
      };
    });
  }, [systemCalls, appRecents, contacts, countryCode]);

  const filtered = useMemo(
    () => (filter === "all" ? rows : rows.filter((r) => r.direction === filter)),
    [rows, filter]
  );
  const missedCount = rows.filter((r) => r.direction === "missed").length;

  const groups = useMemo(() => {
    const map = new Map<string, Row[]>();
    filtered.forEach((r) => {
      const key = dayGroup(r.at);
      const arr = map.get(key) ?? [];
      arr.push(r);
      map.set(key, arr);
    });
    return [...map.entries()];
  }, [filtered]);

  const favContacts = useMemo(
    () => favorites.map((id) => contacts.find((c) => c.recordID === id)).filter((x): x is DeviceContact => !!x),
    [favorites, contacts]
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
      </View>
    );
  }

  const openRow = (r: Row) => {
    if (r.contact) {
      open({ type: "contact", recordID: r.contact.recordID });
    } else {
      openUnknown(r);
    }
  };

  const openUnknown = (r: Row) => {
    // unknown-number actions
    setUnknownSheet(r);
  };

  const [unknownSheet, setUnknownSheet] = useState<Row | null>(null);

  const directionIcon = (d: Row["direction"]): { icon: string; color: string; label: string } => {
    if (d === "missed") return { icon: "phone-missed", color: C.red, label: t("cd.missed") };
    if (d === "incoming") return { icon: "phone-incoming", color: C.green, label: t("cd.incoming") };
    return { icon: "phone-outgoing", color: C.brand, label: t("cd.outgoing") };
  };

  return (
    <View style={{ flex: 1 }}>
      {/* favorites strip */}
      {favContacts.length > 0 && (
        <View style={{ paddingTop: 10 }}>
          <ScrollView horizontal contentContainerStyle={{ paddingHorizontal: 16, gap: 14 }} showsHorizontalScrollIndicator={false}>
            {favContacts.map((c) => (
              <Pressable key={c.recordID} onPress={() => open({ type: "contact", recordID: c.recordID })} style={{ alignItems: "center", width: 62 }}>
                <Avatar contact={c} size={52} />
                <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 4 }} numberOfLines={1}>
                  {(c.givenName || c.displayName || "?").split(" ")[0]}
                </Text>
              </Pressable>
            ))}
          </ScrollView>
        </View>
      )}

      {/* filters */}
      <View style={{ paddingHorizontal: 16, paddingTop: 10, flexDirection: "row", gap: 6 }}>
        <Chip label={t("act.all")} active={filter === "all"} onPress={() => setFilter("all")} />
        <Chip label={t("cd.missed")} count={missedCount} danger={!filter.startsWith("missed") && missedCount > 0} active={filter === "missed"} onPress={() => setFilter("missed")} />
        <Chip label={t("cd.incoming")} active={filter === "incoming"} onPress={() => setFilter("incoming")} />
        <Chip label={t("cd.outgoing")} active={filter === "outgoing"} onPress={() => setFilter("outgoing")} />
      </View>

      <ScrollView contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 20 }}>
        {Platform.OS !== "android" && (
          <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 10, lineHeight: 18 }}>
            {t("cl.appCallsHint")}
          </Text>
        )}
        {groups.length === 0 && (
          <EmptyState
            icon="history"
            title={Platform.OS === "android" ? t("cd.noCallsYet") : t("cd.noCallsYet")}
            hint={t("cd.noCallsHint")}
          />
        )}
        {groups.map(([day, list]) => (
          <View key={day} style={{ marginBottom: 6 }}>
            <Text style={[styles.dayHeader, { color: C.brand }]}>{day}</Text>
            {list.map((r) => {
              const meta = directionIcon(r.direction);
              return (
                <Pressable
                  key={r.key}
                  onPress={() => openRow(r)}
                  android_ripple={{ color: "rgba(128,128,128,0.12)" }}
                  style={({ pressed }) => [styles.callRow, { backgroundColor: pressed ? C.chip : C.card, borderColor: C.line }]}
                >
                  <Avatar contact={r.contact} size={44} />
                  <View style={{ flex: 1, gap: 2 }}>
                    <Text
                      style={{ fontSize: F.body, fontWeight: "700", color: r.direction === "missed" ? C.red : C.ink }}
                      numberOfLines={1}
                    >
                      {r.contact ? r.contact.displayName : r.name || prettyPhone(r.phone, countryCode) || "?"}
                    </Text>
                    <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
                      <Icon name={meta.icon} size={14} color={meta.color} />
                      <Text style={{ fontSize: F.tiny, color: C.sub }}>
                        {meta.label} · {relTime(r.at)}
                        {r.duration > 0 ? ` · ${durationStr(r.duration)}` : ""}
                      </Text>
                    </View>
                  </View>
                  <Pressable
                    onPress={() => r.phone && placeCall(r.phone)}
                    hitSlop={8}
                    android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
                    style={({ pressed }) => [styles.redial, { backgroundColor: pressed ? C.brandSoft : C.cardAlt }]}
                    accessibilityLabel={t("act.call")}
                  >
                    <Icon name="phone-outline" size={20} color={C.brand} />
                  </Pressable>
                </Pressable>
              );
            })}
          </View>
        ))}
      </ScrollView>

      {/* unknown number sheet */}
      {unknownSheet && (
        <View style={StyleSheet.absoluteFill}>
          <Pressable style={[styles.scrim, { backgroundColor: C.scrim }]} onPress={() => setUnknownSheet(null)} />
          <View style={[styles.sheet, { backgroundColor: C.card }]}>
            <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }}>
              {prettyPhone(unknownSheet.phone, countryCode)}
            </Text>
            <Text style={{ fontSize: F.small, color: C.sub, marginTop: 2 }}>
              {fd(unknownSheet.at)} · {ft(unknownSheet.at)}
            </Text>
            <View style={{ gap: 8, marginTop: 16 }}>
              <Btn
                label={t("act.call")}
                icon="phone"
                onPress={() => {
                  const p = unknownSheet.phone;
                  setUnknownSheet(null);
                  placeCall(p);
                }}
              />
              <Btn
                label={t("act.newContact")}
                icon="account-plus-outline"
                variant="soft"
                onPress={async () => {
                  const p = unknownSheet.phone;
                  setUnknownSheet(null);
                  const saved = await openNewContactForm({ phone: p });
                  if (saved) toast(t("ce.contactSaved"), "ok");
                }}
              />
              <Btn
                label={t("act.copy")}
                icon="content-copy"
                variant="secondary"
                onPress={async () => {
                  const ok = await require("../lib/utils").copyText(unknownSheet.phone);
                  toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error");
                  setUnknownSheet(null);
                }}
              />
              {unknownSheet.appId && (
                <Btn
                  label={t("cd.deleteLog")}
                  icon="trash-can-outline"
                  variant="danger"
                  onPress={() => {
                    deleteRecent(unknownSheet.appId!);
                    setUnknownSheet(null);
                    toast(t("cd.logDeleted"), "ok");
                  }}
                />
              )}
            </View>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  dayHeader: { fontSize: F.small, fontWeight: "800", marginTop: 10, marginBottom: 6 },
  callRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 13,
    paddingVertical: 10,
    marginBottom: 6,
    minHeight: 66,
  },
  redial: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  scrim: { flex: 1, justifyContent: "flex-end" },
  sheet: { borderRadius: 22, padding: 20, paddingBottom: 26 },
});

export const _recentsKeep = { Money };
