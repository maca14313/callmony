/* SwipeRow — swipe left/right to reveal actions, tap to open, long-press for menu.
   Pure RN Animated + PanResponder (no extra native deps). */

import React, { useCallback, useRef } from "react";
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
  const pressed = useRef(false);
  const moved = useRef(false);
  const longFired = useRef(false);
  const longTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const leftW = leftActions.length * 62;
  const rightW = rightActions.length * 62;

  const snap = useCallback(
    (to: number) => {
      Animated.timing(x, {
        toValue: to,
        duration: 180,
        useNativeDriver: false,
      }).start();
    },
    [x]
  );

  const pan = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_e, g) => Math.abs(g.dx) > 8 && Math.abs(g.dy) < 12,
      onPanResponderGrant: () => {
        moved.current = true;
        if (longTimer.current) clearTimeout(longTimer.current);
      },
      onPanResponderMove: (_e, g) => {
        const dx = g.dx;
        x.setValue(Math.max(-rightW - 20, Math.min(leftW + 20, dx)));
      },
      onPanResponderRelease: (_e, g) => {
        const dx = g.dx + g.vx * 40;
        if (rightW > 0 && dx < -rightW * 0.45) snap(-rightW);
        else if (leftW > 0 && dx > leftW * 0.45) snap(leftW);
        else snap(0);
      },
      onPanResponderTerminate: () => snap(0),
    })
  ).current;

  const handlePressIn = () => {
    pressed.current = true;
    moved.current = false;
    longFired.current = false;
    if (onLongPress) {
      longTimer.current = setTimeout(() => {
        if (!moved.current) {
          longFired.current = true;
          vibrate(30);
          snap(0);
          onLongPress();
        }
      }, 480);
    }
  };

  const handlePressOut = () => {
    if (longTimer.current) clearTimeout(longTimer.current);
    pressed.current = false;
  };

  const handleTap = () => {
    if (longFired.current || moved.current) return;
    const value = (x as unknown as { _value: number })._value;
    if (Math.abs(value) > 10) {
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
        <Pressable onPressIn={handlePressIn} onPressOut={handlePressOut} onPress={handleTap}>
          {children}
        </Pressable>
      </Animated.View>
    </View>
  );
};

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
