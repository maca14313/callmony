/* Bottom navigation — 6 icon-only tabs (Recents · Contacts · Keypad ·
   Calculator · General · More). A flex sibling of the content column
   with safe-area padding: it can never cover or overlap any component,
   and adjustResize keeps it above the keyboard. 64px touch targets. */

import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { dueStatus } from "../lib/calc";
import { useThemeColors } from "../theme";

const TABS: { key: string; icon: string; labelKey: string }[] = [
  { key: "recents", icon: "history", labelKey: "nav.recents" },
  { key: "contacts", icon: "account-multiple-outline", labelKey: "nav.contacts" },
  { key: "keypad", icon: "dialpad", labelKey: "nav.keypad" },
  { key: "calculator", icon: "calculator", labelKey: "nav.calculator" },
  { key: "general", icon: "wallet-outline", labelKey: "nav.general" },
  { key: "more", icon: "dots-horizontal-circle-outline", labelKey: "nav.more" },
];

export const BottomNav: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();
  const tab = useUI((s) => s.tab);
  const keypadOpen = useUI((s) => s.keypadOpen);
  const moreOpen = useUI((s) => s.moreOpen);
  const setTab = useUI((s) => s.setTab);
  const setKeypadOpen = useUI((s) => s.setKeypadOpen);
  const setMoreOpen = useUI((s) => s.setMoreOpen);
  const closeAll = useUI((s) => s.closeAll);
  const open = useUI((s) => s.open);
  const overdueCount = useApp(
    (s) => s.reminders.filter((r) => !r.deletedAt && !r.done && dueStatus(r.snoozedUntil ?? r.dueAt) === "overdue").length
  );

  const handle = (key: string) => {
    if (key === "keypad") {
      // tapping the keypad tab while the dialer overlay is open closes it
      if (keypadOpen) {
        closeAll();
        return;
      }
      closeAll();
      setKeypadOpen(true);
      return;
    }
    closeAll();
    if (key === "calculator") {
      open({ type: "calculator" });
    } else if (key === "more") {
      setMoreOpen(true);
    } else {
      setTab(key as "recents" | "contacts" | "general" | "reminders" | "trash");
    }
  };

  const isActive = (key: string): boolean => {
    if (key === "keypad") return keypadOpen;
    if (key === "calculator") return false; // overlay handles its own state
    if (key === "more") return moreOpen;
    return tab === key;
  };

  return (
    <View
      accessibilityRole="tablist"
      style={[styles.root, { borderTopColor: C.line, paddingBottom: Math.max(insets.bottom, 6) }]}
    >
      <View style={styles.row}>
        {TABS.map((item) => {
          const active = isActive(item.key);
          const isKeypad = item.key === "keypad";
          const badge = item.key === "more" && overdueCount > 0 ? overdueCount : null;
          return (
            <Pressable
              key={item.key}
              accessibilityRole="tab"
              accessibilityLabel={t(item.labelKey)}
              accessibilityState={{ selected: active }}
              android_ripple={{ color: "rgba(128,128,128,0.16)", borderless: true, radius: 30 }}
              onPress={() => handle(item.key)}
              style={styles.tab}
            >
              <View
                style={[
                  isKeypad ? styles.keypadTile : styles.tile,
                  {
                    backgroundColor: isKeypad ? (active ? "#ffffff" : C.brand) : active ? C.brandSoft : "transparent",
                    borderColor: isKeypad && !active ? C.brand : "transparent",
                    borderWidth: isKeypad ? 1.5 : 0,
                  },
                ]}
              >
                <Icon
                  name={item.icon}
                  size={isKeypad ? 26 : 24}
                  color={isKeypad ? (active ? C.brand : "#ffffff") : active ? C.brand : C.sub}
                />
              </View>
              {badge != null && (
                <View style={[styles.badge, { backgroundColor: C.red }]}>
                  <Text style={styles.badgeText}>{badge > 9 ? "9+" : badge}</Text>
                </View>
              )}
            </Pressable>
          );
        })}
      </View>
    </View>
  );
};


const styles = StyleSheet.create({
  root: {
    borderTopWidth: StyleSheet.hairlineWidth,
    backgroundColor: "transparent",
  },
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-around", paddingTop: 6, height: 70 },
  tab: { flex: 1, alignItems: "center", justifyContent: "center", minHeight: 64, minWidth: 56 },
  tile: {
    width: 48,
    height: 34,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  keypadTile: {
    width: 52,
    height: 40,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  badge: {
    position: "absolute",
    top: 2,
    right: 14,
    minWidth: 19,
    height: 19,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
  },
  badgeText: { color: "#fff", fontSize: 13, fontWeight: "800" },
});
