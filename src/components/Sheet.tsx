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
import { Toaster } from "./kit";

/** Full-screen overlay sheet — slide-up, header with back chevron.
    `headerLeft` (small avatar) + title + subtitle + `headerRight` actions
    form a compact navigation-bar-style header. */
export const Sheet: React.FC<{
  title: string;
  subtitle?: string;
  onBack: () => void;
  headerLeft?: React.ReactNode;
  headerRight?: React.ReactNode;
  footer?: React.ReactNode;
  children: React.ReactNode;
  scroll?: boolean;
  /** v1.5.4 — optional long-press on the subtitle (e.g. copy the number in
      the contact nav bar). Absent → plain Text, fully backward-compatible. */
  onSubtitleLongPress?: () => void;
}> = ({ title, subtitle, onBack, headerLeft, headerRight, footer, children, scroll = true, onSubtitleLongPress }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();

  /* v1.6.10 — FADE instead of SLIDE: Android animates a transparent Modal's
     slide by pushing the WHOLE (heavy) content through a transform layer —
     with a large sheet tree that start frame was the "slow to open the
     contact detail page" jank. The fade is a cheap window alpha and reads
     just as polished. */
  return (
    <Modal transparent animationType="fade" visible onRequestClose={onBack}>
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
            {headerLeft}
            <View style={{ flex: 1, alignItems: "center", minWidth: 0 }}>
              <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }} numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.7}>
                {title}
              </Text>
              {!!subtitle &&
                (onSubtitleLongPress ? (
                  <Pressable onLongPress={onSubtitleLongPress} delayLongPress={400} hitSlop={{ top: 4, bottom: 4, left: 8, right: 8 }}>
                    <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                      {subtitle}
                    </Text>
                  </Pressable>
                ) : (
                  <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                    {subtitle}
                  </Text>
                ))}
            </View>
            <View style={{ alignItems: "flex-end" }}>{headerRight}</View>
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
            <View style={[styles.sheetFooter, { borderTopColor: C.line, paddingBottom: Math.max(insets.bottom, 16) }]}>
              {footer}
            </View>
          )}
          {/* v1.6.1 — the toast layer INSIDE the modal window: a shell-only
              toast was invisible while any sheet was open (native Modals are
              stacked above the app view → "toast shows in another place").
              pointerEvents box-none: taps pass straight through to the sheet. */}
          <Toaster />
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
        {/* v1.6.1 — toast layer inside this modal window too (see Sheet):
            floats above the partial sheet; box-none so taps pass through. */}
        <Toaster />
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
