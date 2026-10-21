/* Keypad — FULL-SCREEN dialer overlay that opens on top of any tab.
   Layout is computed from the window size so the keypad grid is ALWAYS
   100% visible — it is never inside a scroll. The area above the keypad is
   a virtualized live match list: while typing, every matching contact (T9
   name match + number match) appears and one tap places a REAL immediate
   call; with an empty number it shows recent calls instead — exactly like
   the built-in dialer. Long-press 0 → "+", long-press 2–9 → speed dial,
   backspace hold → clear. */

import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  FlatList,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp, contactByPhone } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone, t9Match, vibrate } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { F, useThemeColors } from "../theme";
import { Avatar, EmptyState, toast } from "./kit";

const KEYS: { d: string; letters?: string }[] = [
  { d: "1" },
  { d: "2", letters: "ABC" },
  { d: "3", letters: "DEF" },
  { d: "4", letters: "GHI" },
  { d: "5", letters: "JKL" },
  { d: "6", letters: "MNO" },
  { d: "7", letters: "PQRS" },
  { d: "8", letters: "TUV" },
  { d: "9", letters: "WXYZ" },
  { d: "*" },
  { d: "0", letters: "+" },
  { d: "#" },
];

/* precomputed search entry — built once per contacts change, so each
   keystroke only runs cheap comparisons */
interface MatchEntry {
  c: DeviceContact;
  name: string;
  nameL: string;
  letters: string;
  phones: string[]; // normalized digits
}

/* one row of the list above the keypad */
interface SuggestRow {
  key: string;
  contact?: DeviceContact;
  title: string;
  phone: string;
  info?: string;
  direction?: "incoming" | "outgoing" | "missed";
}

/* ---------- memoized match row (module level → real memoization) ---------- */

const MatchRow: React.FC<{
  row: SuggestRow;
  onCall: (row: SuggestRow) => void;
  onOpenContact: (recordID: string) => void;
}> = memo(({ row, onCall, onOpenContact }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const dir =
    row.direction === "missed"
      ? { icon: "phone-missed", color: C.red }
      : row.direction === "incoming"
        ? { icon: "phone-incoming", color: C.green }
        : row.direction === "outgoing"
          ? { icon: "phone-outgoing", color: C.brand }
          : null;
  return (
    <Pressable
      onPress={() => onCall(row)}
      onLongPress={() => row.contact && onOpenContact(row.contact.recordID)}
      delayLongPress={380}
      android_ripple={{ color: "rgba(128,128,128,0.12)" }}
      style={({ pressed }) => [styles.match, { backgroundColor: pressed ? C.chip : "transparent" }]}
    >
      <Avatar contact={row.contact} size={40} />
      <View style={{ flex: 1, gap: 1, minWidth: 0 }}>
        <Text style={{ color: C.ink, fontWeight: "700", fontSize: F.body, flexShrink: 1 }} numberOfLines={1}>
          {row.title}
        </Text>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 5 }}>
          {dir && <Icon name={dir.icon} size={13} color={dir.color} />}
          <Text style={{ color: C.sub, fontSize: F.small }} numberOfLines={1}>
            {row.info || row.phone}
          </Text>
        </View>
      </View>
      <Icon name="phone-outline" size={20} color={C.brand} />
    </Pressable>
  );
});

/* ---------------- the sheet ---------------- */

export const KeypadSheet: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();
  const { height, width } = useWindowDimensions();
  const setKeypadOpen = useUI((s) => s.setKeypadOpen);
  const open = useUI((s) => s.open);
  const countryCode = useApp((s) => s.settings.countryCode);
  const contacts = useApp((s) => s.deviceContacts);
  const speedDialSettings = useApp((s) => s.settings.speedDial);
  const contactX = useApp((s) => s.contactX);
  const systemCalls = useApp((s) => s.systemCalls);
  const appRecents = useApp((s) => s.recents);

  const [number, setNumber] = useState("");
  const clearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longFired = useRef(false);

  useEffect(() => {
    return () => {
      if (clearTimer.current) clearTimeout(clearTimer.current);
    };
  }, []);

  /* ---------- keypad sizing: ALWAYS fits, never scrolls ---------- */
  const CHROME =
    58 /*display*/ + 92 /*controls*/ + 18 /*keypad top pad*/ + 30 /*row gaps*/ + 16 /*bottom pad*/;
  const keyH = Math.max(42, Math.min(82, Math.floor((height - insets.top - insets.bottom - CHROME - 80) / 4)));
  const keyW = Math.min(keyH, Math.floor((width - 36 /*h padding*/ - 28 /*gaps*/) / 3));

  /* ---------- contact search index (built once per contacts load) ---------- */
  const index = useMemo<MatchEntry[]>(
    () =>
      contacts.map((c) => {
        const name = displayName(c);
        return {
          c,
          name,
          nameL: name.toLowerCase(),
          letters: name.toLowerCase().replace(/[^a-z]/g, ""),
          phones: c.phones.map((p) => normalizePhone(p.number, countryCode).replace(/\D/g, "")),
        };
      }),
    [contacts, countryCode]
  );

  /* ---------- live matches while typing (ALL matches, capped) ---------- */
  const digits = number.replace(/\D/g, "");
  const matches = useMemo<MatchEntry[]>(() => {
    if (digits.length < 1) return [];
    const query = number.toLowerCase();
    const out: { e: MatchEntry; score: number }[] = [];
    for (const e of index) {
      let score = -1;
      if (digits.length >= 2 && e.letters && t9Match(digits, e.name)) score = 3;
      else if (query && e.nameL.includes(query)) score = 2;
      if (digits.length >= 2) {
        for (const p of e.phones) {
          if (p.includes(digits) || digits.includes(p.slice(-digits.length))) {
            score = 4;
            break;
          }
        }
      }
      if (score > 0) {
        out.push({ e, score });
        if (out.length >= 240) break;
      }
    }
    out.sort((a, b) => b.score - a.score || a.e.name.localeCompare(b.e.name));
    return out.map((x) => x.e);
  }, [digits, number, index]);

  const exactMatch = useMemo(() => {
    if (digits.length < 6) return null;
    const typed = normalizePhone(number, countryCode).replace(/\D/g, "");
    return (
      contacts.find((c) =>
        c.phones.some((p) => normalizePhone(p.number, countryCode).replace(/\D/g, "") === typed)
      ) ?? null
    );
  }, [digits, number, contacts, countryCode]);

  /* ---------- list rows: matches (typed) or recent calls (empty) ---------- */
  const listRows = useMemo<SuggestRow[]>(() => {
    if (digits.length > 0) {
      return matches.map((e) => ({
        key: e.c.recordID,
        contact: e.c,
        title: e.name,
        phone: e.c.phones[0]?.number ?? "",
        info: e.c.phones[0] ? prettyPhone(e.c.phones[0].number, countryCode) : t("cd.noPhone"),
      }));
    }
    const source = Platform.OS === "android" ? systemCalls.slice(0, 80) : appRecents.slice(0, 80);
    return source.map((r) => {
      const phone = (r as { phoneNumber?: string }).phoneNumber ?? (r as { phone?: string }).phone ?? "";
      const cachedName = (r as { name?: string }).name;
      const contact = phone ? contactByPhone(phone) : undefined;
      const known = phone.replace(/\D/g, "").replace(/^0+/, "").length >= 3;
      return {
        key: (r as { id: string }).id,
        contact,
        title:
          contact?.displayName || contact?.givenName || cachedName ||
          (known ? prettyPhone(phone, countryCode) : t("cl.unknownNumber")),
        phone,
        info: known ? prettyPhone(phone, countryCode) : "",
        direction: (r as { direction?: SuggestRow["direction"] }).direction,
      };
    });
  }, [digits, matches, systemCalls, appRecents, countryCode, t]);

  /* ---------- actions ---------- */
  const stopHold = () => {
    if (clearTimer.current) clearTimeout(clearTimer.current);
    clearTimer.current = null;
  };

  const startBackspaceHold = () => {
    stopHold();
    clearTimer.current = setTimeout(() => {
      longFired.current = true;
      setNumber("");
      vibrate(24);
    }, 550);
  };

  const backspace = () => {
    if (longFired.current) {
      longFired.current = false;
      return;
    }
    setNumber((n) => n.slice(0, -1));
    vibrate(8);
  };

  const pressKey = (d: string) => {
    vibrate(10);
    setNumber((n) => (n.length >= 24 ? n : n + d));
  };

  const longPressKey = (d: string) => {
    if (d === "0") {
      setNumber((n) => (n.length >= 24 ? n : n + "+"));
      vibrate(14);
      return;
    }
    if (d >= "2" && d <= "9") {
      const recordID = contactX.find((x) => x.speedDial === d)?.recordID ?? speedDialSettings?.[d];
      const target = recordID ? contacts.find((c) => c.recordID === recordID) : null;
      if (target) {
        const phone = target.phones[0]?.number;
        if (phone) {
          toast(`${t("act.call")} · ${displayName(target)}`, "info");
          placeCall(phone);
          setKeypadOpen(false);
          return;
        }
      }
      open({ type: "speedDial" });
    }
  };

  const call = async () => {
    if (!number) return;
    vibrate(16);
    const res = await placeCall(number);
    if (res === "blocked") toast(t("cd.blocked"), "error");
    else if (res === "denied" || res === "dialer") toast(t("cl.continuedInDialer"), "info");
    else if (res === "failed") toast(t("act.call") + " ✕", "error");
  };

  const callRow = useCallback((row: SuggestRow) => {
    const phone = row.contact?.phones[0]?.number ?? row.phone;
    if (!phone) return;
    vibrate(12);
    placeCall(phone);
  }, []);

  const openContact = useCallback(
    (recordID: string) => {
      setKeypadOpen(false);
      open({ type: "contact", recordID });
    },
    [open, setKeypadOpen]
  );

  const addContact = async () => {
    if (!number) return;
    const saved = await openNewContactForm({ phone: number });
    if (saved) toast(t("ce.contactSaved"), "ok");
  };

  const close = () => setKeypadOpen(false);

  const renderRow = useCallback(
    ({ item }: { item: SuggestRow }) => <MatchRow row={item} onCall={callRow} onOpenContact={openContact} />,
    [callRow, openContact]
  );

  return (
    <Modal
      animationType="slide"
      visible
      onRequestClose={close}
      statusBarTranslucent
    >
      <View style={[styles.root, { backgroundColor: C.canvas, paddingTop: insets.top, paddingBottom: insets.bottom }]}>
        {/* display row: close · number · backspace */}
        <View style={[styles.displayRow, { direction: "ltr" }]}>
          <Pressable
            onPress={close}
            hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
            android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true, radius: 24 }}
            style={styles.closeBtn}
            accessibilityLabel={t("act.close")}
          >
            <Icon name="chevron-down" size={28} color={C.sub} />
          </Pressable>
          <View style={{ flex: 1, alignItems: "center", minWidth: 0 }}>
            {number ? (
              <Text
                style={[styles.number, { color: C.ink }]}
                adjustsFontSizeToFit
                minimumFontScale={0.4}
                numberOfLines={1}
              >
                {number.startsWith("*") || number.includes("#") ? number : prettyPhone(number, countryCode)}
              </Text>
            ) : (
              <Text style={[styles.placeholder, { color: C.faint }]}>{t("kp.enterNumber")}</Text>
            )}
            {!!exactMatch && (
              <Pressable
                onPress={() => {
                  setKeypadOpen(false);
                  open({ type: "contact", recordID: exactMatch.recordID });
                }}
              >
                <View style={[styles.exactPill, { backgroundColor: C.brandSoft }]}>
                  <Avatar contact={exactMatch} size={22} />
                  <Text style={{ color: C.brand, fontWeight: "700", fontSize: F.small }} numberOfLines={1}>
                    {displayName(exactMatch)}
                  </Text>
                </View>
              </Pressable>
            )}
          </View>
          <Pressable
            onPress={backspace}
            onPressIn={startBackspaceHold}
            onPressOut={stopHold}
            style={styles.backspace}
            android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
            accessibilityLabel={t("kp.deleteDigit")}
          >
            <Icon name="backspace-outline" size={26} color={number ? C.ink : C.faint} />
          </Pressable>
        </View>

        {/* live matches / recents — the ONLY scrollable part */}
        <FlatList
          data={listRows}
          keyExtractor={(item) => item.key}
          renderItem={renderRow}
          keyboardShouldPersistTaps="handled"
          initialNumToRender={12}
          windowSize={7}
          contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: 8 }}
          ListEmptyComponent={
            digits.length > 0 ? (
              <EmptyState icon="magnify-close" title={t("sr.noResults")} hint={t("sr.tryHint")} />
            ) : (
              <View style={{ alignItems: "center", paddingVertical: 26 }}>
                <Icon name="dialpad" size={34} color={C.faint} />
                <Text style={{ color: C.sub, fontSize: F.small, marginTop: 8 }}>{t("kp.readyToConnect")}</Text>
              </View>
            )
          }
          style={{ flex: 1 }}
        />

        {/* keypad grid — FIXED at the bottom, always 100% visible */}
        <View style={{ paddingHorizontal: 18, gap: 10, direction: "ltr" }}>
          {[0, 1, 2, 3].map((row) => (
            <View key={row} style={{ flexDirection: "row", gap: 14, justifyContent: "center" }}>
              {KEYS.slice(row * 3, row * 3 + 3).map((k) => (
                <Pressable
                  key={k.d}
                  onPress={() => pressKey(k.d)}
                  onLongPress={() => longPressKey(k.d)}
                  delayLongPress={380}
                  android_ripple={{ color: "rgba(128,128,128,0.22)", borderless: true, radius: keyW * 0.5 }}
                  style={({ pressed }) => [
                    styles.key,
                    {
                      width: keyW,
                      height: keyH,
                      backgroundColor: pressed ? C.chip : C.card,
                      borderColor: C.line,
                      opacity: pressed ? 0.8 : 1,
                    },
                  ]}
                >
                  <Text style={{ fontSize: Math.min(keyH * 0.42, 30), fontWeight: "600", color: C.ink }}>{k.d}</Text>
                  {!!k.letters && (
                    <Text style={{ fontSize: 12, fontWeight: "700", color: C.faint, letterSpacing: 1.1, minHeight: 14 }}>
                      {k.letters}
                    </Text>
                  )}
                </Pressable>
              ))}
            </View>
          ))}
        </View>

        {/* call + add-contact row */}
        <View style={[styles.controls, { direction: "ltr" }]}>
          <Pressable
            onPress={() => open({ type: "speedDial" })}
            style={styles.sideBtn}
            android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
            accessibilityLabel={t("kp.speedDial")}
          >
            <Icon name="grid" size={24} color={C.sub} />
          </Pressable>
          <Pressable
            onPress={call}
            disabled={!number}
            android_ripple={{ color: "rgba(255,255,255,0.3)", borderless: true, radius: 40 }}
            style={({ pressed }) => [
              styles.callBtn,
              { backgroundColor: number ? C.brand : C.faint, opacity: pressed ? 0.85 : 1 },
            ]}
            accessibilityLabel={t("kp.callNumber")}
          >
            <Icon name="phone" size={30} color="#fff" />
          </Pressable>
          <Pressable
            onPress={addContact}
            disabled={!number}
            style={[styles.sideBtn, { opacity: number ? 1 : 0.4 }]}
            android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
            accessibilityLabel={t("act.newContact")}
          >
            <Icon name="account-plus-outline" size={24} color={C.ink} />
          </Pressable>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  root: { flex: 1 },
  displayRow: { flexDirection: "row", alignItems: "center", gap: 6, minHeight: 58, paddingHorizontal: 12 },
  closeBtn: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center" },
  number: { fontSize: 28, fontWeight: "800", maxWidth: 230 },
  placeholder: { fontSize: 20, fontWeight: "600" },
  exactPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
    marginTop: 2,
  },
  backspace: { width: 52, height: 44, alignItems: "center", justifyContent: "center" },
  match: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderRadius: 14,
    paddingHorizontal: 12,
    paddingVertical: 8,
    minHeight: 60,
  },
  key: {
    borderRadius: 999,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 1,
  },
  controls: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 12,
    marginBottom: 4,
    paddingHorizontal: 22,
    minHeight: 80,
  },
  callBtn: { width: 72, height: 72, borderRadius: 36, alignItems: "center", justifyContent: "center", elevation: 6 },
  sideBtn: { width: 58, height: 58, borderRadius: 29, alignItems: "center", justifyContent: "center" },
});
