/* SwipeRow — swipe left/right to reveal actions, tap to open, long-press for
   menu. Pure RN Animated + PanResponder (no extra native deps).

   v1.6.2 — TAP vs LONG-PRESS misfire fixed (user report: "when I click the
   call card sometimes it acts as long press"): the old custom 480ms timer
   had no touch-slop cancellation, so a slightly slow tap opened the action
   sheet instead of the contact. Long-press now uses RN Pressable's native
   detection (cancels on any finger movement or scroll take-over) with a
   650ms threshold — a click always stays a click, a real hold always opens
   the sheet.

   Gesture detection is HORIZONTAL-DOMINANT: a drag is only claimed when the
   horizontal movement clearly outweighs the vertical movement, so swiping a
   row sideways never scrolls the list downward (and the list scrolling
   downward never half-opens a row). Rows are memoized and only ~15 are
   mounted at a time (virtualized lists), so even 1000+ contacts stay smooth. */

import React, { memo, useCallback, useRef } from "react";
import { Animated, PanResponder, Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { vibrate } from "../lib/utils";
import { F, useThemeColors } from "../theme";

export interface SwipeAction {
  icon: string;
  label?: string;
  color: string;
  onPress: () => void;
}

export const SwipeRow: React.FC<{
  children: React.ReactNode;
  leftActions?: SwipeAction[]; // revealed when swiping right
  rightActions?: SwipeAction[]; // revealed when swiping left
  onTap?: () => void;
  onLongPress?: () => void;
}> = ({ children, leftActions = [], rightActions = [], onTap, onLongPress }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const x = useRef(new Animated.Value(0)).current;
  const openRef = useRef(0); // current snapped offset (0 = closed)
  const moved = useRef(false);

  const leftW = leftActions.length * 62;
  const rightW = rightActions.length * 62;

  const snap = useCallback(
    (to: number) => {
      openRef.current = to;
      Animated.timing(x, { toValue: to, duration: 160, useNativeDriver: false }).start();
    },
    [x]
  );

  /* claim the gesture ONLY when the drag is clearly horizontal */
  const isHorizontalDrag = (_e: unknown, g: { dx: number; dy: number }) =>
    Math.abs(g.dx) > 12 && Math.abs(g.dx) > Math.abs(g.dy) * 1.25;

  const pan = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: isHorizontalDrag,
      onMoveShouldSetPanResponderCapture: isHorizontalDrag,
      onPanResponderGrant: () => {
        moved.current = true;
      },
      onPanResponderMove: (_e, g) => {
        // drag relative to the current open position → re-swiping an open row works
        x.setValue(Math.max(-rightW - 24, Math.min(leftW + 24, openRef.current + g.dx)));
      },
      onPanResponderRelease: (_e, g) => {
        const total = openRef.current + g.dx + g.vx * 30;
        if (rightW > 0 && total < -rightW * 0.4) snap(-rightW);
        else if (leftW > 0 && total > leftW * 0.4) snap(leftW);
        else snap(0);
      },
      onPanResponderTerminate: () => snap(0),
    })
  ).current;

  /* native Pressable long-press: cancels on touch-slop movement / scroll
     take-over, and RN never fires onPress afterwards — no custom timer */
  const handleLongPress = () => {
    vibrate(30);
    snap(0);
    onLongPress?.();
  };

  const handleTap = () => {
    if (moved.current) return;
    if (openRef.current !== 0) {
      snap(0);
      return;
    }
    onTap?.();
  };

  const renderActions = (actions: SwipeAction[], side: "left" | "right") => (
    <View
      style={[
        styles.actions,
        side === "left" ? { left: 0, flexDirection: "row" } : { right: 0, flexDirection: "row-reverse" },
      ]}
    >
      {actions.map((a, i) => (
        <Pressable
          key={`${a.icon}-${i}`}
          style={[styles.actionBtn, { backgroundColor: a.color }]}
          android_ripple={{ color: "rgba(255,255,255,0.25)", borderless: true }}
          onPress={() => {
            snap(0);
            a.onPress();
          }}
        >
          <Icon name={a.icon} size={22} color="#fff" />
          {!!a.label && (
            <Text style={styles.actionLabel} numberOfLines={1}>
              {a.label}
            </Text>
          )}
        </Pressable>
      ))}
    </View>
  );

  return (
    <View style={{ overflow: "hidden", borderRadius: 18 }}>
      {leftActions.length > 0 && renderActions(leftActions, "left")}
      {rightActions.length > 0 && renderActions(rightActions, "right")}
      <Animated.View
        {...pan.panHandlers}
        style={[{ transform: [{ translateX: x }] }, { backgroundColor: C.card }]}
      >
        <Pressable
          pressRetentionOffset={{ left: 8, right: 8, top: 4, bottom: 4 }}
          onPressIn={() => {
            moved.current = false;
          }}
          onPress={handleTap}
          onLongPress={onLongPress ? handleLongPress : undefined}
          delayLongPress={650}
        >
          {children}
        </Pressable>
      </Animated.View>
    </View>
  );
};

/** Memoized wrapper — rows re-render only when their own props change. */
export const MemoSwipeRow = memo(SwipeRow);

const styles = StyleSheet.create({
  actions: { position: "absolute", top: 0, bottom: 0, alignItems: "center" },
  actionBtn: {
    width: 58,
    height: "100%",
    minHeight: 64,
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
    borderRadius: 12,
    marginHorizontal: 2,
    alignSelf: "center",
  },
  actionLabel: { color: "#fff", fontSize: F.tiny, fontWeight: "700", maxWidth: 54 },
});
