/* Sheets — full-screen Sheet (overlay) + partial BottomSheet (scrollable) */

import React, { useState } from "react";
import {
  Dimensions,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { F, useThemeColors } from "../theme";

/** Full-screen overlay sheet — slide-up, header with back chevron. */
export const Sheet: React.FC<{
  title: string;
  subtitle?: string;
  onBack: () => void;
  headerRight?: React.ReactNode;
  footer?: React.ReactNode;
  children: React.ReactNode;
  scroll?: boolean;
}> = ({ title, subtitle, onBack, headerRight, footer, children, scroll = true }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const insets = useSafeAreaInsets();

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onBack}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <View style={[styles.sheetRoot, { backgroundColor: C.canvas, paddingTop: insets.top }]}>
          <View style={[styles.sheetHeader, { borderBottomColor: C.line }]}>
            <Pressable onPress={onBack} hitSlop={10} style={styles.backBtn}>
              <Icon name="chevron-left" size={26} color={C.ink} />
            </Pressable>
            <View style={{ flex: 1, alignItems: "center" }}>
              <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }} numberOfLines={1}>
                {title}
              </Text>
              {!!subtitle && (
                <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                  {subtitle}
                </Text>
              )}
            </View>
            <View style={{ minWidth: 34, alignItems: "flex-end" }}>{headerRight}</View>
          </View>
          <View style={{ flex: 1 }}>
            {scroll ? (
              <ScrollView contentContainerStyle={styles.sheetBody} keyboardShouldPersistTaps="handled">
                {children}
              </ScrollView>
            ) : (
              children
            )}
          </View>
          {!!footer && (
            <View style={[styles.sheetFooter, { borderTopColor: C.line, paddingBottom: Math.max(insets.bottom, 12) }]}>
              {footer}
            </View>
          )}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

/** Partial bottom sheet — drag handle, scrim tap-to-close, scrollable body. */
export const BottomSheet: React.FC<{
  title?: string;
  onClose: () => void;
  children: React.ReactNode;
  maxHeightPct?: number;
}> = ({ title, onClose, children, maxHeightPct = 0.86 }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const insets = useSafeAreaInsets();
  const [maxH] = useState(Dimensions.get("window").height * maxHeightPct);

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onClose}>
      <Pressable style={[styles.scrim, { backgroundColor: C.scrim }]} onPress={onClose} />
      <KeyboardAvoidingView
        style={{ justifyContent: "flex-end", flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <View
          style={[
            styles.bottomSheet,
            {
              backgroundColor: C.card,
              maxHeight: maxH,
              paddingBottom: Math.max(insets.bottom, 14),
            },
          ]}
        >
          <View style={styles.grabberRow}>
            <View style={[styles.grabber, { backgroundColor: C.line }]} />
            {!!title && (
              <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink, marginTop: 10 }}>{title}</Text>
            )}
          </View>
          <ScrollView
            contentContainerStyle={{ paddingHorizontal: 18, paddingBottom: 10 }}
            keyboardShouldPersistTaps="handled"
            nestedScrollEnabled
          >
            {children}
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  sheetRoot: { flex: 1 },
  sheetHeader: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 10,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  backBtn: { width: 38, height: 44, alignItems: "center", justifyContent: "center" },
  sheetBody: { padding: 18, paddingBottom: 40 },
  sheetFooter: { borderTopWidth: StyleSheet.hairlineWidth, paddingHorizontal: 16, paddingTop: 12 },
  scrim: { position: "absolute", top: 0, left: 0, right: 0, bottom: 0 },
  bottomSheet: { borderTopLeftRadius: 26, borderTopRightRadius: 26, elevation: 12 },
  grabberRow: { alignItems: "center", paddingTop: 10, paddingHorizontal: 18, paddingBottom: 6 },
  grabber: { width: 44, height: 5, borderRadius: 3 },
});
