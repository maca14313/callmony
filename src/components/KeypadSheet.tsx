/* Keypad — webapp-style BOTTOM SHEET that rises over the Recents tab.
   Layout top→bottom: grabber + header (speed-dial · “Keypad” · close) ·
   live match list (while typing) · NUMBER DISPLAY (right above the keys) ·
   3×4 key grid (max-w 300, always fully visible) · video / call / add row.
   Prefix search matches BOTH formats: “+251…” (international) and “09…”
   (local trunk) hit the same contact. Long-press 0 → “+”, long-press 2–9 →
   speed dial, hold backspace → clear. */

import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Animated,
  Easing,
  FlatList,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone, t9Match, vibrate } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { F, useThemeColors } from "../theme";
import { Avatar, toast } from "./kit";

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
   keystroke only runs cheap comparisons. Every phone is indexed in BOTH
   raw form (“0911…”, “+251911…” as typed) and normalized E.164-ish digits,
   so a query starting with “+251” OR “09” (or plain “9…”) all match. */
interface MatchEntry {
  c: DeviceContact;
  name: string;
  letters: string;
  phones: { raw: string; norm: string; number: string }[];
}

/* one row of the list above the display */
interface SuggestRow {
  key: string;
  contact?: DeviceContact;
  title: string;
  phone: string;
  info?: string;
}

/* ---------- memoized match row (module level → real memoization) ---------- */

const MatchRow: React.FC<{
  row: SuggestRow;
  onCall: (row: SuggestRow) => void;
  onOpenContact: (recordID: string) => void;
}> = memo(({ row, onCall, onOpenContact }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Pressable
      onPress={() => onCall(row)}
      onLongPress={() => row.contact && onOpenContact(row.contact.recordID)}
      delayLongPress={380}
      android_ripple={{ color: "rgba(128,128,128,0.12)" }}
      style={({ pressed }) => [styles.match, { backgroundColor: pressed ? C.chip : "transparent" }]}
    >
      <Avatar contact={row.contact} size={36} />
      <View style={{ flex: 1, gap: 1, minWidth: 0 }}>
        <Text style={{ color: C.ink, fontWeight: "700", fontSize: F.small, flexShrink: 1 }} numberOfLines={1}>
          {row.title}
        </Text>
        <Text style={{ color: C.sub, fontSize: F.tiny }} numberOfLines={1}>
          {row.info || row.phone}
        </Text>
      </View>
      <Icon name="phone-outline" size={19} color={C.brand} />
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

  const [number, setNumber] = useState("");
  const clearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longFired = useRef(false);

  /* entrance animation — panel slides up (native driver), scrim fades in */
  const anim = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.parallel([
      Animated.timing(anim, {
        toValue: 1,
        duration: 260,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
    ]).start();
  }, [anim]);
  const closingRef = useRef(false);
  const close = useCallback(() => {
    if (closingRef.current) return;
    closingRef.current = true;
    Animated.timing(anim, { toValue: 0, duration: 170, useNativeDriver: true }).start(() =>
      setKeypadOpen(false)
    );
  }, [anim, setKeypadOpen]);

  useEffect(() => {
    return () => {
      if (clearTimer.current) clearTimeout(clearTimer.current);
    };
  }, []);

  /* ---------- keypad sizing: webapp clamps, always fully visible ---------- */
  const gridW = Math.min(width - 36, 300);
  const keyW = Math.floor((gridW - 32 /* grid h-padding */ - 28 /* 2 gaps */) / 3);
  const keyH = Math.max(44, Math.min(62, Math.floor(height * 0.082)));
  const callSize = Math.max(54, Math.min(64, Math.floor(height * 0.08)));

  /* ---------- contact search index (built once per contacts load) ---------- */
  const index = useMemo<MatchEntry[]>(
    () =>
      contacts.map((c) => {
        const name = displayName(c);
        return {
          c,
          name,
          letters: name.toLowerCase().replace(/[^a-z]/g, ""),
          phones: c.phones.map((p) => ({
            number: p.number,
            raw: p.number.replace(/\D/g, ""),
            norm: normalizePhone(p.number, countryCode).replace(/\D/g, ""),
          })),
        };
      }),
    [contacts, countryCode]
  );

  /* ---------- live matches while typing ----------
     Prefix search: the typed digits must appear in the RAW number (covers
     “09…” local format) or in the NORMALIZED number (covers “+251…”), so
     both common Ethiopian formats find the same contact. */
  const digits = number.replace(/\D/g, "");
  const matches = useMemo<MatchEntry[]>(() => {
    if (digits.length < 2) return [];
    const out: { e: MatchEntry; score: number }[] = [];
    for (const e of index) {
      let score = -1;
      if (e.letters && t9Match(digits, e.name)) score = 3;
      for (const p of e.phones) {
        if (!digits) break;
        if (p.raw.startsWith(digits) || p.norm.startsWith(digits)) {
          score = 4;
          break;
        }
        if (p.raw.includes(digits) || p.norm.includes(digits)) {
          score = Math.max(score, 3.5);
          break;
        }
      }
      if (score > 0) {
        out.push({ e, score });
        if (out.length >= 60) break;
      }
    }
    out.sort((a, b) => b.score - a.score || a.e.name.localeCompare(b.e.name));
    return out.map((x) => x.e);
  }, [digits, index]);

  const exactMatch = useMemo(() => {
    if (digits.length < 6) return null;
    const typed = normalizePhone(number, countryCode).replace(/\D/g, "");
    return (
      contacts.find((c) =>
        c.phones.some((p) => normalizePhone(p.number, countryCode).replace(/\D/g, "") === typed)
      ) ?? null
    );
  }, [digits, number, contacts, countryCode]);

  /* ---------- list rows (typed → matching contacts) ---------- */
  const listRows = useMemo<SuggestRow[]>(
    () =>
      matches.map((e) => ({
        key: e.c.recordID,
        contact: e.c,
        title: e.name,
        phone: e.c.phones[0]?.number ?? "",
        info: e.c.phones[0] ? prettyPhone(e.c.phones[0].number, countryCode) : t("cd.noPhone"),
      })),
    [matches, countryCode, t]
  );

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
          close();
          return;
        }
      }
      open({ type: "speedDial" });
    }
  };

  const call = async () => {
    if (!number) {
      toast(t("kp.enterNumber"), "info");
      return;
    }
    vibrate(16);
    close();
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
      close();
      open({ type: "contact", recordID });
    },
    [open, close]
  );

  const addContact = async () => {
    if (!number) return;
    const saved = await openNewContactForm({ phone: number });
    if (saved) toast(t("ce.contactSaved"), "ok");
  };

  const renderRow = useCallback(
    ({ item }: { item: SuggestRow }) => <MatchRow row={item} onCall={callRow} onOpenContact={openContact} />,
    [callRow, openContact]
  );

  const showList = digits.length >= 2 && listRows.length > 0;

  return (
    <Modal transparent animationType="none" visible statusBarTranslucent onRequestClose={close}>
      <View style={{ flex: 1, justifyContent: "flex-end" }}>
        {/* scrim — tap anywhere above the panel closes (deepest hit target) */}
        <AnimatedPressable
          onPress={close}
          accessibilityLabel={t("act.close")}
          accessibilityRole="button"
          style={{ ...StyleSheet.absoluteFillObject, backgroundColor: C.scrim, opacity: anim }}
        />

        {/* panel */}
        <Animated.View
          style={[
            styles.panel,
            {
              backgroundColor: C.canvas,
              borderTopColor: C.line,
              paddingBottom: Math.max(insets.bottom, 12),
              maxHeight: Math.floor(height * 0.96),
              opacity: anim,
              transform: [
                {
                  translateY: anim.interpolate({
                    inputRange: [0, 1],
                    outputRange: [Math.min(height * 0.5, 420), 0],
                  }),
                },
              ],
            },
          ]}
        >
          {/* header — grabber + speed-dial · title · close */}
          <Pressable onPress={close} hitSlop={{ top: 6, bottom: 6 }} style={styles.grabberRow}>
            <View style={[styles.grabber, { backgroundColor: C.line }]} />
          </Pressable>
          <View style={styles.headerRow}>
            <Pressable
              onPress={() => {
                close();
                open({ type: "speedDial" });
              }}
              hitSlop={10}
              style={styles.headerBtn}
              accessibilityLabel={t("kp.speedDial")}
            >
              <Icon name="grid" size={17} color={C.sub} />
            </Pressable>
            <Text style={styles.headerTitle}>{t("nav.keypad")}</Text>
            <Pressable onPress={close} hitSlop={10} style={styles.headerBtn} accessibilityLabel={t("act.close")}>
              <Icon name="close" size={18} color={C.sub} />
            </Pressable>
          </View>

          {/* live matches — compact list, only while typing (shrinks first
              when the panel hits its height cap on short screens) */}
          {showList && (
            <View style={{ maxHeight: Math.floor(height * 0.24), flexShrink: 1 }}>
              <FlatList
                data={listRows.slice(0, 40)}
                keyExtractor={(item) => item.key}
                renderItem={renderRow}
                keyboardShouldPersistTaps="handled"
                initialNumToRender={6}
                maxToRenderPerBatch={8}
                windowSize={5}
                contentContainerStyle={{ paddingHorizontal: 14, paddingBottom: 4 }}
                ListEmptyComponent={null}
                style={{ flexGrow: 0 }}
              />
            </View>
          )}

          {/* number display — DIRECTLY above the keypad grid */}
          <View style={[styles.displayRow, { direction: "ltr" }]}>
            <View style={{ flex: 1, alignItems: "center", justifyContent: "center", minWidth: 0 }}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 4 }}>
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
                {!!number && (
                  <Pressable
                    onPress={backspace}
                    onPressIn={startBackspaceHold}
                    onPressOut={stopHold}
                    hitSlop={6}
                    style={styles.backspace}
                    accessibilityLabel={t("kp.deleteDigit")}
                  >
                    <Icon name="backspace-outline" size={21} color={C.sub} />
                  </Pressable>
                )}
              </View>
              {exactMatch ? (
                <Pressable
                  onPress={() => {
                    close();
                    open({ type: "contact", recordID: exactMatch.recordID });
                  }}
                  style={({ pressed }) => [
                    styles.exactPill,
                    { backgroundColor: pressed ? C.chip : C.brandSoft },
                  ]}
                >
                  <Avatar contact={exactMatch} size={20} />
                  <Text style={{ color: C.brand, fontWeight: "700", fontSize: F.tiny }} numberOfLines={1}>
                    {displayName(exactMatch)}
                  </Text>
                </Pressable>
              ) : (
                !!listRows[0] && (
                  <Text style={{ color: C.brand, fontSize: F.tiny, fontWeight: "600", marginTop: 2 }} numberOfLines={1}>
                    {listRows[0].title}
                  </Text>
                )
              )}
            </View>
          </View>

          {/* keypad grid — max-w 300, centered, never scrolls */}
          <View style={{ width: gridW, alignSelf: "center", gap: 10, direction: "ltr" }}>
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
                        opacity: pressed ? 0.85 : 1,
                      },
                    ]}
                  >
                    <Text style={{ fontSize: Math.min(keyH * 0.42, 26), fontWeight: "500", color: C.ink }}>
                      {k.d}
                    </Text>
                    {!!k.letters && (
                      <Text style={{ fontSize: 13, fontWeight: "600", color: C.faint, letterSpacing: 2, minHeight: 15 }}>
                        {k.letters}
                      </Text>
                    )}
                  </Pressable>
                ))}
              </View>
            ))}
          </View>

          {/* video · call · add-contact */}
          <View style={[styles.controls, { width: gridW, alignSelf: "center", direction: "ltr" }]}>
            <Pressable
              onPress={() => toast(t("kp.videoDemo"), "info")}
              style={({ pressed }) => [
                styles.sideBtn,
                { borderColor: C.line, backgroundColor: pressed ? C.chip : C.card },
              ]}
              accessibilityLabel={t("kp.videoCall")}
            >
              <Icon name="video-outline" size={19} color={C.sub} />
            </Pressable>
            <Pressable
              onPress={call}
              disabled={!number}
              android_ripple={{ color: "rgba(255,255,255,0.3)", borderless: true, radius: callSize * 0.5 }}
              style={({ pressed }) => [
                styles.callBtn,
                { width: callSize, height: callSize, borderRadius: callSize / 2, backgroundColor: number ? C.brand : C.faint, opacity: pressed ? 0.86 : 1 },
              ]}
              accessibilityLabel={number ? t("kp.callNumber", { number }) : t("act.call")}
            >
              <Icon name="phone" size={26} color="#fff" />
            </Pressable>
            <Pressable
              onPress={addContact}
              disabled={!number}
              style={({ pressed }) => [
                styles.sideBtn,
                { borderColor: C.line, backgroundColor: pressed ? C.chip : C.card, opacity: number ? 1 : 0.5 },
              ]}
              accessibilityLabel={t("act.newContact")}
            >
              <Icon name="account-plus-outline" size={20} color={C.sub} />
            </Pressable>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
};

/* Pressable + Animated opacity — small helper (avoids Animated.Component typing noise) */
const AnimatedPressable: React.FC<{
  onPress: () => void;
  accessibilityLabel?: string;
  accessibilityRole?: "button";
  style: object;
}> = ({ onPress, accessibilityLabel, accessibilityRole, style }) => (
  <Animated.View style={style} onStartShouldSetResponder={() => true} onResponderRelease={onPress} accessible accessibilityLabel={accessibilityLabel} accessibilityRole={accessibilityRole} />
);

const styles = StyleSheet.create({
  panel: {
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    borderTopWidth: 1,
    paddingTop: 8,
    elevation: 18,
    shadowColor: "#000",
    shadowOpacity: 0.22,
    shadowRadius: 24,
    shadowOffset: { width: 0, height: -6 },
  },
  grabberRow: { alignItems: "center", paddingBottom: 2 },
  grabber: { width: 40, height: 4, borderRadius: 2 },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 14,
    paddingTop: 2,
    paddingBottom: 6,
    minHeight: 44,
  },
  headerBtn: { width: 36, height: 36, borderRadius: 18, alignItems: "center", justifyContent: "center" },
  headerTitle: { fontSize: F.small, fontWeight: "700", letterSpacing: 1, color: "#7a8c88" },
  match: {
    flexDirection: "row",
    alignItems: "center",
    gap: 11,
    borderRadius: 14,
    paddingHorizontal: 10,
    paddingVertical: 6,
    minHeight: 52,
  },
  displayRow: {
    minHeight: 62,
    maxHeight: 84,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 18,
    paddingVertical: 6,
  },
  number: { fontSize: 28, fontWeight: "500", letterSpacing: 1.2, maxWidth: 250 },
  placeholder: { fontSize: F.small, fontWeight: "600", letterSpacing: 2.5, textTransform: "uppercase" },
  exactPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 7,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 4,
    marginTop: 3,
  },
  backspace: { width: 38, height: 38, borderRadius: 19, alignItems: "center", justifyContent: "center" },
  key: {
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    gap: 1,
    elevation: 2,
    shadowColor: "#0e2a26",
    shadowOpacity: 0.1,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
  },
  controls: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 20,
    paddingTop: 12,
    marginTop: 2,
    minHeight: 76,
  },
  callBtn: {
    alignItems: "center",
    justifyContent: "center",
    elevation: 8,
    shadowColor: "#16a085",
    shadowOpacity: 0.4,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
  },
  sideBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
