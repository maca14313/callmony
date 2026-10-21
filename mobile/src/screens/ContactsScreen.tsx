/* Contacts tab — every phone contact in a smooth, searchable FlatList. */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { FlatList, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, prettyPhone } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import {
  checkContactsPermission,
  fetchAllContacts,
  openExistingContactEditor,
  requestContactsPermission,
} from "../native/deviceContacts";
import { openURL, smsLink, tgLink, waLink } from "../lib/share";
import { shareContactVcard } from "../lib/exporters";
import { F, useThemeColors } from "../theme";
import { Avatar, Chip, EmptyState, Money, Pill, Spinner } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

type SortMode = "az" | "balance" | "recent";

export const ContactsScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const contacts = useApp((s) => s.deviceContacts);
  const entries = useApp((s) => s.entries);
  const contactX = useApp((s) => s.contactX);
  const blocked = useApp((s) => s.settings.blockedNumbers);
  const setDeviceContacts = useApp((s) => s.setDeviceContacts);
  const toggleFavorite = useApp((s) => s.toggleFavorite);
  const toggleBlocked = useApp((s) => s.toggleBlocked);
  const countryCode = useApp((s) => s.settings.countryCode);

  const [perm, setPerm] = useState<"loading" | "authorized" | "denied" | "undetermined">("loading");
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<"all" | "favorites" | "theyOwe" | "youOwe" | "settled">("all");
  const [sort, setSort] = useState<SortMode>("az");
  const [menuFor, setMenuFor] = useState<string | null>(null);

  const load = useCallback(async () => {
    setPerm("loading");
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
  }, [setDeviceContacts]);

  useEffect(() => {
    if (contacts.length === 0 && perm === "loading") load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const favSet = useMemo(() => new Set(contactX.filter((c) => c.favorite).map((c) => c.recordID)), [contactX]);
  const balances = useMemo(() => {
    const map = new Map<string, number>();
    entries.forEach((e) => {
      if (e.deletedAt || !e.ownerId.startsWith("c:")) return;
      const id = e.ownerId.slice(2);
      const signed = (e.type === "credit" ? 1 : -1) * (e.amount - e.payments.reduce((s, p) => s + p.amount, 0));
      map.set(id, Math.round(((map.get(id) ?? 0) + signed) * 100) / 100);
    });
    return map;
  }, [entries]);

  const visible = useMemo(() => {
    let list = contacts;
    if (filter === "favorites") list = list.filter((c) => favSet.has(c.recordID));
    if (filter === "theyOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) > 0);
    if (filter === "youOwe") list = list.filter((c) => (balances.get(c.recordID) ?? 0) < 0);
    if (filter === "settled") list = list.filter((c) => (balances.get(c.recordID) ?? 0) === 0);
    const query = q.trim().toLowerCase();
    if (query) {
      list = list.filter(
        (c) =>
          displayName(c).toLowerCase().includes(query) ||
          c.company?.toLowerCase().includes(query) ||
          c.phones.some((p) => p.number.replace(/\D/g, "").includes(query.replace(/\D/g, "")))
      );
    }
    if (sort === "balance")
      list = [...list].sort((a, b) => Math.abs(balances.get(b.recordID) ?? 0) - Math.abs(balances.get(a.recordID) ?? 0));
    else if (sort === "recent")
      list = [...list].sort((a, b) => Number(b.recordID || 0) - Number(a.recordID || 0));
    return list;
  }, [contacts, filter, q, sort, favSet, balances]);

  /* summary strip */
  const owedToYou = useMemo(
    () => entries.filter((e) => !e.deletedAt && e.ownerId.startsWith("c:") && e.type === "credit").reduce((s, e) => s + (e.amount - e.payments.reduce((x, p) => x + p.amount, 0)), 0),
    [entries]
  );
  const youOwe = useMemo(
    () => entries.filter((e) => !e.deletedAt && e.ownerId.startsWith("c:") && e.type === "debit").reduce((s, e) => s + (e.amount - e.payments.reduce((x, p) => x + p.amount, 0)), 0),
    [entries]
  );

  const sections = useMemo(() => {
    if (sort !== "az") return [{ letter: "", data: visible }];
    const groups = new Map<string, typeof visible>();
    visible.forEach((c) => {
      const letter = (displayName(c)[0] || "#").toUpperCase();
      const key = /[A-Z]/.test(letter) ? letter : "#";
      const arr = groups.get(key) ?? [];
      arr.push(c);
      groups.set(key, arr);
    });
    return [...groups.entries()].sort((a, b) => (a[0] === "#" ? 1 : b[0] === "#" ? -1 : a[0].localeCompare(b[0]))).map(([letter, data]) => ({ letter, data }));
  }, [visible, sort]);

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

      {/* list */}
      <FlatList
        data={sections}
        keyExtractor={(s) => s.letter || "all"}
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 20 }}
        ListEmptyComponent={
          <EmptyState
            icon="account-search-outline"
            title={q ? t("cd.noMatchingContacts") : t("cd.noContactsYet")}
            hint={q ? t("sr.tryHint") : t("cd.noContactsHint")}
          />
        }
        ListHeaderComponent={
          sections.length > 0 ? (
            <Text style={{ color: C.sub, fontSize: F.small, marginBottom: 8, paddingHorizontal: 4 }}>
              {t("cd.contactsCount", { n: visible.length })}
            </Text>
          ) : null
        }
        renderItem={({ item }) => (
          <View>
            {item.letter !== "" && (
              <Text style={[styles.letterHeader, { color: C.brand }]}>{item.letter}</Text>
            )}
            {item.data.map((c) => {
              const balance = balances.get(c.recordID) ?? 0;
              const isFav = favSet.has(c.recordID);
              const isBlocked = blocked.some((b) => {
                const digits = b.replace(/\D/g, "");
                return c.phones.some((p) => p.number.replace(/\D/g, "") === digits);
              });
              return (
                <View key={c.recordID} style={{ marginBottom: 8 }}>
                  <SwipeRow
                    onTap={() => open({ type: "contact", recordID: c.recordID })}
                    onLongPress={() => setMenuFor(c.recordID)}
                    leftActions={[
                      {
                        icon: "phone",
                        color: C.brand,
                        onPress: () => {
                          const phone = c.phones[0]?.number;
                          if (phone) placeCall(phone);
                        },
                      },
                      { icon: isFav ? "star" : "star-outline", color: "#b97d10", onPress: () => toggleFavorite(c.recordID) },
                    ]}
                    rightActions={[
                      {
                        icon: "whatsapp",
                        color: "#25D366",
                        onPress: () => {
                          const phone = c.phones[0]?.number;
                          if (phone) openURL(waLink(phone, undefined, countryCode));
                        },
                      },
                      {
                        icon: "message-text-outline",
                        color: "#4a7d9b",
                        onPress: () => {
                          const phone = c.phones[0]?.number;
                          if (phone) openURL(smsLink(phone));
                        },
                      },
                    ]}
                  >
                    <View style={[styles.row, { backgroundColor: C.card, borderColor: C.line }]}>
                      <Avatar contact={c} size={46} />
                      <View style={{ flex: 1, gap: 2 }}>
                        <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
                          <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                            {displayName(c)}
                          </Text>
                          {isFav && <Icon name="star" size={13} color="#b97d10" />}
                          {isBlocked && <Pill label={t("cd.blocked")} tone="red" />}
                        </View>
                        <Text style={{ fontSize: F.small, color: C.sub }} numberOfLines={1}>
                          {c.phones[0]
                            ? prettyPhone(c.phones[0].number, countryCode)
                            : c.emails[0]?.address || c.company || t("cd.noPhone")}
                        </Text>
                      </View>
                      {balance !== 0 && <Money value={balance} colorize signed size={15} />}
                      <Icon name="chevron-right" size={20} color={C.faint} />
                    </View>
                  </SwipeRow>
                </View>
              );
            })}
          </View>
        )}
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

const ContactMenuSheet: React.FC<{
  onClose: () => void;
  recordID: string;
  contact?: DeviceContact;
}> = ({ onClose, recordID, contact }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const favSet = new Set(useApp((s) => s.contactX.filter((c) => c.favorite).map((c) => c.recordID)));
  const toggleFavorite = useApp((s) => s.toggleFavorite);
  const blocked = useApp((s) => s.settings.blockedNumbers);
  const toggleBlocked = useApp((s) => s.toggleBlocked);
  const phone = contact?.phones[0]?.number;

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
    {
      icon: "telegram",
      label: t("act.telegram"),
      run: () => phone && openURL(tgLink(`+${phone.replace(/\D/g, "")}`)),
      disabled: !phone,
    },
    { icon: "pencil-outline", label: t("act.edit"), run: editNow },
    { icon: favSet.has(recordID) ? "star-off-outline" : "star", label: favSet.has(recordID) ? t("cd.removedFromFavorites") : t("cd.addToFavorites"), run: () => toggleFavorite(recordID) },
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

  function isBlockedNow(): boolean {
    if (!phone) return false;
    const digits = phone.replace(/\D/g, "");
    return blocked.some((b) => b.replace(/\D/g, "") === digits);
  }

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
            style={({ pressed }) => [menuStyles.item, { backgroundColor: pressed ? C.chip : "transparent", opacity: it.disabled ? 0.4 : 1 }]}
          >
            <Icon name={it.icon} size={21} color={it.color || C.sub} />
            <Text style={{ fontSize: F.body, color: it.color || C.ink, fontWeight: "600" }}>{it.label}</Text>
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
  statCard: { flex: 1, borderRadius: 14, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 9, gap: 2 },
  search: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, height: 46, fontSize: F.body },
  sortBtn: { flexDirection: "row", alignItems: "center", gap: 5, borderRadius: 999, borderWidth: 1, paddingHorizontal: 11, paddingVertical: 7, minHeight: 36 },
  letterHeader: { fontSize: F.small, fontWeight: "800", marginTop: 10, marginBottom: 6, paddingHorizontal: 2 },
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
