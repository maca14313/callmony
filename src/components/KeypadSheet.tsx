/* Keypad — half-modal dialer. Smart matching (T9 + name + phone) while
   typing, backspace (hold = clear), long-press 0 → "+", long-press 2–9 →
   speed dial. The big call button places a REAL immediate call. */

import React, { useEffect, useMemo, useRef, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { displayName, normalizePhone, prettyPhone, t9Match, vibrate } from "../lib/utils";
import { contactOwnerId } from "../lib/types";
import { placeCall } from "../native/calls";
import { openNewContactForm } from "../native/deviceContacts";
import { F, useThemeColors } from "../theme";
import { Avatar, toast } from "./kit";
import { BottomSheet } from "./Sheet";

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

export const KeypadSheet: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const setKeypadOpen = useUI((s) => s.setKeypadOpen);
  const open = useUI((s) => s.open);
  const countryCode = useApp((s) => s.settings.countryCode);
  const contacts = useApp((s) => s.deviceContacts);
  const speedDialSettings = useApp((s) => s.settings.speedDial);
  const contactX = useApp((s) => s.contactX);

  const [number, setNumber] = useState("");
  const clearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longFired = useRef(false);

  useEffect(() => {
    return () => {
      if (clearTimer.current) clearTimeout(clearTimer.current);
    };
  }, []);

  /* smart matching — top 3 suggestions */
  const suggestions = useMemo(() => {
    if (number.length < 1) return [];
    const digits = number.replace(/\D/g, "");
    const scored = contacts
      .map((c) => {
        const name = displayName(c);
        let score = -1;
        if (digits.length >= 2 && t9Match(digits, name)) score = 3;
        else if (name.toLowerCase().includes(number.toLowerCase())) score = 2;
        if (score < 0 && digits.length >= 3) {
          const hit = c.phones.some((p) => p.number.replace(/\D/g, "").includes(digits));
          if (hit) score = 1;
        }
        return { c, name, score };
      })
      .filter((x) => x.score > 0)
      .sort((a, b) => b.score - a.score || a.name.localeCompare(b.name))
      .slice(0, 3);
    return scored;
  }, [number, contacts]);

  const exactMatch = useMemo(() => {
    if (number.replace(/\D/g, "").length < 6) return null;
    const typed = normalizePhone(number, countryCode);
    return contacts.find((c) => c.phones.some((p) => normalizePhone(p.number, countryCode) === typed)) ?? null;
  }, [number, contacts, countryCode]);

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
    }, 600);
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
      // speed dial
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

  const addContact = async () => {
    if (!number) return;
    const saved = await openNewContactForm({ phone: number });
    if (saved) toast(t("ce.contactSaved"), "ok");
  };

  return (
    <BottomSheet onClose={() => setKeypadOpen(false)} maxHeightPct={0.72}>
      {/* number display */}
      <View style={styles.displayRow}>
        <Pressable
          onPress={() => open({ type: "speedDial" })}
          style={styles.speedBtn}
          android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
        >
          <Icon name="grid" size={20} color={C.sub} />
        </Pressable>
        <View style={{ flex: 1, alignItems: "center" }}>
          {number ? (
            <Text style={[styles.number, { color: C.ink }]} adjustsFontSizeToFit minimumFontScale={0.4} numberOfLines={1}>
              {number.startsWith("*") || number.includes("#") ? number : prettyPhone(number, countryCode)}
            </Text>
          ) : (
            <Text style={[styles.placeholder, { color: C.faint }]}>{t("kp.enterNumber")}</Text>
          )}
          {!!exactMatch && (
            <Pressable onPress={() => { setKeypadOpen(false); open({ type: "contact", recordID: exactMatch.recordID }); }}>
              <View style={[styles.exactPill, { backgroundColor: C.brandSoft }]}>
                <Avatar contact={exactMatch} size={22} />
                <Text style={{ color: C.brand, fontWeight: "700", fontSize: F.small }}>
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

      {/* suggestions */}
      {suggestions.length > 0 && !exactMatch && (
        <View style={{ gap: 6, marginBottom: 8 }}>
          {suggestions.map(({ c, name }) => (
            <Pressable
              key={c.recordID}
              onPress={() => { setKeypadOpen(false); open({ type: "contact", recordID: c.recordID }); }}
              android_ripple={{ color: "rgba(128,128,128,0.12)" }}
              style={({ pressed }) => [styles.suggest, { backgroundColor: pressed ? C.chip : C.cardAlt }]}
            >
              <Avatar contact={c} size={30} />
              <View style={{ flex: 1 }}>
                <Text style={{ color: C.ink, fontWeight: "700", fontSize: F.small }} numberOfLines={1}>{name}</Text>
                <Text style={{ color: C.sub, fontSize: F.tiny }} numberOfLines={1}>
                  {c.phones[0] ? prettyPhone(c.phones[0].number, countryCode) : t("cd.noPhone")}
                </Text>
              </View>
              <Icon name="phone-outline" size={18} color={C.brand} />
            </Pressable>
          ))}
        </View>
      )}

      {/* keypad grid */}
      <View style={{ gap: 10 }}>
        {[0, 1, 2, 3].map((row) => (
          <View key={row} style={{ flexDirection: "row", gap: 14 }}>
            {KEYS.slice(row * 3, row * 3 + 3).map((k) => (
              <Pressable
                key={k.d}
                onPress={() => pressKey(k.d)}
                onLongPress={() => longPressKey(k.d)}
                delayLongPress={380}
                android_ripple={{ color: "rgba(128,128,128,0.22)", borderless: true, radius: 46 }}
                style={({ pressed }) => [
                  styles.key,
                  { backgroundColor: pressed ? C.chip : C.card, borderColor: C.line, opacity: pressed ? 0.8 : 1 },
                ]}
              >
                <Text style={{ fontSize: F.keypad, fontWeight: "600", color: C.ink }}>{k.d}</Text>
                {!!k.letters && (
                  <Text style={{ fontSize: 13, fontWeight: "700", color: C.faint, letterSpacing: 1.2, minHeight: 16 }}>
                    {k.letters}
                  </Text>
                )}
              </Pressable>
            ))}
          </View>
        ))}
      </View>

      {/* call + add */}
      <View style={styles.controls}>
        <View style={{ width: 62 }} />
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
          <Icon name="account-plus-outline" size={26} color={C.ink} />
        </Pressable>
      </View>
    </BottomSheet>
  );
};


const styles = StyleSheet.create({
  displayRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 10, minHeight: 58 },
  speedBtn: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  number: { fontSize: 28, fontWeight: "800", maxWidth: 220 },
  placeholder: { fontSize: 20, fontWeight: "600" },
  exactPill: { flexDirection: "row", alignItems: "center", gap: 8, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4, marginTop: 4 },
  backspace: { width: 52, height: 44, alignItems: "center", justifyContent: "center" },
  suggest: { flexDirection: "row", alignItems: "center", gap: 10, borderRadius: 14, paddingHorizontal: 12, paddingVertical: 7 },
  key: {
    flex: 1,
    aspectRatio: 1,
    maxWidth: 88,
    borderRadius: 999,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  controls: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 16, paddingHorizontal: 8 },
  callBtn: { width: 74, height: 74, borderRadius: 37, alignItems: "center", justifyContent: "center", elevation: 6 },
  sideBtn: { width: 62, height: 62, borderRadius: 31, alignItems: "center", justifyContent: "center" },
});
