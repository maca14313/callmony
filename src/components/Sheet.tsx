/* Sheets — full-screen Sheet (overlay) + partial BottomSheet.
   BottomSheet closes FOUR ways: scrim tap · grabber/header tap · X button ·
   hardware back. The scrim is the FIRST child inside the flex-end container
   so it is the deepest hit-test target at every point above the sheet —
   taps can never be swallowed by a full-screen sibling (the bug that made
   sheets feel impossible to close without the hardware back button). */

import React from "react";
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  useWindowDimensions,
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
  const t = useT();
  const insets = useSafeAreaInsets();

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onBack}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
        <View style={[styles.sheetRoot, { backgroundColor: C.canvas, paddingTop: insets.top }]}>
          <View style={[styles.sheetHeader, { borderBottomColor: C.line }]}>
            <Pressable
              onPress={onBack}
              hitSlop={{ top: 12, bottom: 12, left: 8, right: 12 }}
              style={styles.backBtn}
              accessibilityRole="button"
              accessibilityLabel={t("act.back")}
            >
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

/** Partial bottom sheet — scrim tap, header/grabber tap, X button and the
    hardware back key all close it. The sheet body scrolls; the header never
    scrolls away, so there is always a visible close affordance. */
export const BottomSheet: React.FC<{
  title?: string;
  onClose: () => void;
  children: React.ReactNode;
  maxHeightPct?: number;
  noScroll?: boolean;
}> = ({ title, onClose, children, maxHeightPct = 0.86, noScroll = false }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();
  const { height } = useWindowDimensions();
  const maxH = height * maxHeightPct;

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onClose}>
      {/* container: scrim is the FIRST child → deepest hit target everywhere
          above the sheet; the KAV below only wraps the sheet itself. */}
      <View style={{ flex: 1, justifyContent: "flex-end" }}>
        <Pressable
          style={[styles.scrim, { backgroundColor: C.scrim }]}
          onPress={onClose}
          accessibilityLabel={t("act.close")}
          accessibilityRole="button"
        />
        <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined} style={{ maxHeight: maxH }}>
          <View style={[styles.bottomSheet, { backgroundColor: C.card, maxHeight: maxH, paddingBottom: Math.max(insets.bottom, 14) }]}>
            {/* header: tap anywhere (grabber / title / X) to close */}
            <Pressable
              onPress={onClose}
              hitSlop={{ top: 6, bottom: 6 }}
              style={styles.grabberRow}
              accessibilityRole="button"
              accessibilityLabel={t("act.close")}
            >
              <View style={[styles.grabber, { backgroundColor: C.line }]} />
              <View style={styles.titleRow}>
                {!!title && (
                  <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink, flexShrink: 1 }} numberOfLines={1}>
                    {title}
                  </Text>
                )}
                <Icon name="close" size={22} color={C.sub} />
              </View>
            </Pressable>
            {noScroll ? (
              <View style={{ paddingHorizontal: 18, paddingBottom: 10 }}>{children}</View>
            ) : (
              <ScrollView
                contentContainerStyle={{ paddingHorizontal: 18, paddingBottom: 10 }}
                keyboardShouldPersistTaps="handled"
                nestedScrollEnabled
              >
                {children}
              </ScrollView>
            )}
          </View>
        </KeyboardAvoidingView>
      </View>
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
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginTop: 10,
    alignSelf: "stretch",
    justifyContent: "center",
    minHeight: 30,
  },
});
