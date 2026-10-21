/* callmony — shared UI kit (primitives, toast, confirm) */

import React, { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { create } from "zustand";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { avatarColors, displayName, initialsOf, money as fmtMoney } from "../lib/utils";
import type { DeviceContact } from "../lib/types";
import { F, ThemeColors, useThemeColors } from "../theme";

/* ---------------- toast ---------------- */

interface ToastItem {
  id: number;
  msg: string;
  kind: "ok" | "error" | "info";
  action?: { label: string; run: () => void };
}

interface ToastState {
  items: ToastItem[];
  push: (msg: string, kind: ToastItem["kind"], action?: ToastItem["action"]) => void;
  drop: (id: number) => void;
}

const useToastStore = create<ToastState>((set) => ({
  items: [],
  push: (msg, kind, action) =>
    set((s) => ({ items: [...s.items, { id: Date.now() + Math.random(), msg, kind, action }] })),
  drop: (id) => set((s) => ({ items: s.items.filter((t) => t.id !== id) })),
}));

export const toast = (msg: string, kind: ToastItem["kind"] = "ok") =>
  useToastStore.getState().push(msg, kind);
export const toastUndo = () => {
  const { lastUndo, undo } = useApp.getState();
  if (!lastUndo) return;
  useToastStore.getState().push(lastUndo.label, "info", {
    label: "Undo",
    run: undo,
  });
};

export const Toaster: React.FC = () => {
  const items = useToastStore((s) => s.items);
  const drop = useToastStore((s) => s.drop);
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();

  useEffect(() => {
    if (!items.length) return;
    const timer = setTimeout(() => drop(items[0].id), items[0].action ? 5000 : 3000);
    return () => clearTimeout(timer);
  }, [items, drop]);

  if (!items.length) return null;
  const first = items[0];

  return (
    <View pointerEvents="box-none" style={styles.toastWrap}>
      <View
        style={[
          styles.toast,
          {
            backgroundColor: C.mode === "dark" ? "#243230" : "#1f2f2c",
            borderColor: first.kind === "error" ? C.red : undefined,
          },
        ]}
      >
        <Text style={styles.toastMsg} numberOfLines={2}>
          {first.msg}
        </Text>
        {first.action && (
          <Pressable
            hitSlop={8}
            onPress={() => {
              first.action!.run();
              drop(first.id);
            }}
          >
            <Text style={[styles.toastAction, { color: C.brand }]}>{t("act.undo")}</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
};

/* ---------------- confirm ---------------- */

interface ConfirmReq {
  id: number;
  title: string;
  message?: string;
  confirmLabel?: string;
  danger?: boolean;
  resolve: (v: boolean) => void;
}

interface ConfirmState {
  req: ConfirmReq | null;
  ask: (r: Omit<ConfirmReq, "id" | "resolve">) => Promise<boolean>;
  answer: (v: boolean) => void;
}

const useConfirmStore = create<ConfirmState>((set, get) => ({
  req: null,
  ask: (r) =>
    new Promise<boolean>((resolve) => {
      set({ req: { ...r, id: Date.now(), resolve } });
    }),
  answer: (v) => {
    get().req?.resolve(v);
    set({ req: null });
  },
}));

export const confirm = (title: string, message?: string, confirmLabel?: string, danger?: boolean) =>
  useConfirmStore.getState().ask({ title, message, confirmLabel, danger });

export const ConfirmHost: React.FC = () => {
  const req = useConfirmStore((s) => s.req);
  const answer = useConfirmStore((s) => s.answer);
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  if (!req) return null;

  return (
    <Modal transparent animationType="fade" visible onRequestClose={() => answer(false)}>
      <View style={[styles.scrim, { backgroundColor: C.scrim }]}>
        <View style={[styles.dialog, { backgroundColor: C.card }]}>
          <Text style={[styles.dialogTitle, { color: C.ink }]}>{req.title}</Text>
          {!!req.message && (
            <Text style={[styles.dialogMsg, { color: C.sub }]}>{req.message}</Text>
          )}
          <View style={styles.dialogRow}>
            <Btn label={t("act.cancel")} variant="ghost" onPress={() => answer(false)} style={{ flex: 1 }} />
            <Btn
              label={req.confirmLabel || t("misc.confirm")}
              variant={req.danger ? "danger" : "primary"}
              onPress={() => answer(true)}
              style={{ flex: 1 }}
            />
          </View>
        </View>
      </View>
    </Modal>
  );
};

/* ---------------- money ---------------- */

export const Money: React.FC<{
  value: number;
  currency?: string;
  signed?: boolean;
  colorize?: boolean;
  size?: number;
  weight?: "400" | "500" | "600" | "700" | "800" | "900";
}> = ({ value, currency, signed, colorize, size = 15, weight = "600" }) => {
  const appCurrency = useApp((s) => s.settings.currency);
  const hide = useApp((s) => s.settings.hideBalances);
  const C = useThemeColors(useApp((s) => s.settings.theme));
  if (hide) return <Text style={{ fontSize: size, fontWeight: weight, color: C.faint }}>•••</Text>;
  const text = fmtMoney(value, currency || appCurrency, { signed });
  const color = colorize ? (value > 0 ? C.green : value < 0 ? C.red : C.sub) : undefined;
  return (
    <Text style={{ fontSize: size, fontWeight: weight, color: color ?? C.ink }}>{text}</Text>
  );
};

/* ---------------- avatar ---------------- */

export const Avatar: React.FC<{ contact?: DeviceContact | null; size?: number; photo?: string | null }> = ({
  contact,
  size = 46,
  photo,
}) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const palette = avatarColors(contact);
  const source = photo || contact?.thumbnailPath;
  if (source) {
    return (
      <View style={[styles.avatar, { width: size, height: size, borderRadius: size / 2, backgroundColor: palette.color }]}>
        <AvatarImage source={source} size={size} />
      </View>
    );
  }
  return (
    <View
      style={[
        styles.avatar,
        { width: size, height: size, borderRadius: size / 2, backgroundColor: palette.color },
      ]}
    >
      <Text style={{ color: palette.text, fontWeight: "700", fontSize: Math.max(size * 0.36, 13) }}>
        {initialsOf(contact)}
      </Text>
    </View>
  );
};

const AvatarImage: React.FC<{ source: string; size: number }> = ({ source, size }) => {
  const [failed, setFailed] = useState(false);
  if (failed) return null;
  const uri = source.startsWith("file:") || source.startsWith("content:") || source.startsWith("/")
    ? source.startsWith("/") ? `file://${source}` : source
    : `file://${source}`;
  return (
    <Image
      source={{ uri }}
      style={{ width: size, height: size, borderRadius: size / 2 }}
      onError={() => setFailed(true)}
    />
  );
};

/* ---------------- buttons / chips / cards ---------------- */

export const Btn: React.FC<{
  label?: string;
  onPress: () => void;
  variant?: "primary" | "secondary" | "danger" | "ghost" | "soft";
  icon?: string;
  style?: object;
  disabled?: boolean;
  small?: boolean;
}> = ({ label, onPress, variant = "primary", icon, style, disabled, small }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const bg =
    variant === "primary"
      ? C.brand
      : variant === "danger"
        ? C.red
        : variant === "secondary"
          ? C.card
          : variant === "soft"
            ? C.brandSoft
            : "transparent";
  const fg =
    variant === "primary" || variant === "danger"
      ? C.onBrand
      : variant === "soft"
        ? C.brand
        : variant === "secondary"
          ? C.ink
          : C.sub;
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      android_ripple={{ color: "rgba(128,128,128,0.18)", borderless: false }}
      style={({ pressed }) => [
        styles.btn,
        small && styles.btnSmall,
        { backgroundColor: bg, opacity: disabled ? 0.45 : pressed ? 0.88 : 1, borderColor: C.line, borderWidth: variant === "secondary" ? 1 : 0 },
        style,
      ]}
    >
      {!!icon && <Icon name={icon} size={18} color={fg} />}
      {!!label && <Text style={{ color: fg, fontSize: small ? 14 : 16, fontWeight: "600" }}>{label}</Text>}
    </Pressable>
  );
};

export const IconBtn: React.FC<{
  icon: string;
  onPress: () => void;
  size?: number;
  color?: string;
  bg?: string;
  danger?: boolean;
}> = ({ icon, onPress, size = 40, color, bg, danger }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Pressable
      onPress={onPress}
      hitSlop={6}
      android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true }}
      style={({ pressed }) => [
        styles.iconBtn,
        { width: size, height: size, borderRadius: size / 2 },
        { backgroundColor: bg || (pressed ? C.chip : "transparent") },
      ]}
    >
      <Icon name={icon} size={Math.max(size * 0.5, 18)} color={color || (danger ? C.red : C.sub)} />
    </Pressable>
  );
};

export const Chip: React.FC<{
  label: string;
  active?: boolean;
  onPress?: () => void;
  icon?: string;
  danger?: boolean;
  count?: number;
}> = ({ label, active, onPress, icon, danger, count }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const body = (
    <View
      style={[
        styles.chip,
        {
          backgroundColor: active ? C.brand : C.chip,
          borderColor: active ? C.brand : C.line,
        },
      ]}
    >
      {!!icon && <Icon name={icon} size={15} color={active ? C.onBrand : danger ? C.red : C.sub} />}
      <Text
        numberOfLines={1}
        style={{ fontSize: 14, fontWeight: active ? "700" : "500", color: active ? C.onBrand : danger ? C.red : C.ink }}
      >
        {label}
      </Text>
      {!!count && count > 0 && (
        <View style={[styles.chipCount, { backgroundColor: active ? C.onBrand : danger ? C.red : C.brand }]}>
          <Text style={{ fontSize: 13, fontWeight: "700", color: active ? C.brand : "#fff" }}>{count > 9 ? "9+" : count}</Text>
        </View>
      )}
    </View>
  );
  if (!onPress) return body;
  return (
    <Pressable onPress={onPress} hitSlop={4}>
      {body}
    </Pressable>
  );
};

export const Card: React.FC<{ children: React.ReactNode; style?: object; onPress?: () => void }> = ({
  children,
  style,
  onPress,
}) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const inner = (
    <View style={[styles.card, { backgroundColor: C.card, borderColor: C.line }, style]}>{children}</View>
  );
  if (!onPress) return inner;
  return (
    <Pressable onPress={onPress} android_ripple={{ color: "rgba(128,128,128,0.12)" }}>
      {inner}
    </Pressable>
  );
};

export const SectionHeader: React.FC<{
  title: string;
  right?: React.ReactNode;
  subtitle?: string;
}> = ({ title, right, subtitle }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <View style={styles.sectionHeader}>
      <View style={{ flex: 1 }}>
        <Text style={{ fontSize: F.body, fontWeight: "800", color: C.ink }}>{title}</Text>
        {!!subtitle && <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 1 }}>{subtitle}</Text>}
      </View>
      {right}
    </View>
  );
};

export const EmptyState: React.FC<{
  icon: string;
  title: string;
  hint?: string;
  actionLabel?: string;
  onAction?: () => void;
}> = ({ icon, title, hint, actionLabel, onAction }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <View style={styles.empty}>
      <View style={[styles.emptyIcon, { backgroundColor: C.brandSoft }]}>
        <Icon name={icon} size={30} color={C.brand} />
      </View>
      <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink, marginTop: 12 }}>{title}</Text>
      {!!hint && (
        <Text style={{ fontSize: F.small, color: C.sub, marginTop: 4, textAlign: "center", lineHeight: 20 }}>
          {hint}
        </Text>
      )}
      {!!actionLabel && !!onAction && (
        <View style={{ marginTop: 14 }}>
          <Btn label={actionLabel} onPress={onAction} small />
        </View>
      )}
    </View>
  );
};

export const Segmented: React.FC<{
  options: { key: string; label: string; count?: number }[];
  value: string;
  onChange: (key: string) => void;
  scroll?: boolean;
}> = ({ options, value, onChange, scroll }) => {
  const wrapStyle = scroll
    ? { flexDirection: "row" as const }
    : { flexDirection: "row" as const, flexWrap: "wrap" as const };
  return (
    <View style={[{ gap: 6 }, wrapStyle]}>
      {options.map((o) => (
        <Chip key={o.key} label={o.label} active={value === o.key} count={o.count} onPress={() => onChange(o.key)} />
      ))}
    </View>
  );
};

export const Field: React.FC<{
  label?: string;
  value: string;
  onChangeText: (v: string) => void;
  placeholder?: string;
  keyboardType?: "default" | "numeric" | "phone-pad" | "email-address" | "decimal-pad";
  multiline?: boolean;
  secure?: boolean;
  right?: React.ReactNode;
  autoFocus?: boolean;
  onSubmitEditing?: () => void;
  style?: object;
}> = ({ label, value, onChangeText, placeholder, keyboardType, multiline, right, autoFocus, onSubmitEditing, style }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <View style={{ marginBottom: 12 }}>
      {!!label && <Text style={{ fontSize: F.small, fontWeight: "600", color: C.sub, marginBottom: 6 }}>{label}</Text>}
      <View style={[styles.field, { borderColor: C.line, backgroundColor: C.inputBg }, style]}>
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={C.faint}
          keyboardType={keyboardType}
          multiline={multiline}
          autoFocus={autoFocus}
          onSubmitEditing={onSubmitEditing}
          style={{ flex: 1, fontSize: F.body, color: C.ink, paddingVertical: multiline ? 10 : 0, minHeight: 48, textAlignVertical: multiline ? "top" : "center" }}
        />
        {right}
      </View>
    </View>
  );
};

export const Divider: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: C.line, marginVertical: 10 }} />;
};

export const Pill: React.FC<{
  label: string;
  tone?: "brand" | "green" | "red" | "amber" | "neutral" | "blue";
  icon?: string;
}> = ({ label, tone = "neutral", icon }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const map: Record<string, { bg: string; fg: string }> = {
    brand: { bg: C.brandSoft, fg: C.brand },
    green: { bg: "rgba(29,154,107,0.14)", fg: C.green },
    red: { bg: "rgba(214,69,69,0.13)", fg: C.red },
    amber: { bg: "rgba(185,125,16,0.14)", fg: C.amber },
    blue: { bg: "rgba(74,125,155,0.14)", fg: C.blue },
    neutral: { bg: C.chip, fg: C.sub },
  };
  const m = map[tone];
  return (
    <View style={[styles.pill, { backgroundColor: m.bg }]}>
      {!!icon && <Icon name={icon} size={13} color={m.fg} />}
      <Text style={{ fontSize: F.tiny, fontWeight: "700", color: m.fg }} numberOfLines={1}>
        {label}
      </Text>
    </View>
  );
};

export const Spinner: React.FC<{ size?: number | "small" | "large" }> = ({ size = 22 }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return <ActivityIndicator size={size as "small" | "large" | number} color={C.brand} />;
};

/* helper — contact name fallback */
export const contactName = (c?: DeviceContact | null): string => displayName(c);

const styles = StyleSheet.create({
  toastWrap: { position: "absolute", left: 0, right: 0, bottom: 96, alignItems: "center", paddingHorizontal: 20 },
  toast: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
    maxWidth: 380,
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  toastMsg: { color: "#e8f1ef", fontSize: 14, flexShrink: 1 },
  toastAction: { fontSize: 14, fontWeight: "800" },
  scrim: { flex: 1, justifyContent: "center", alignItems: "center", padding: 28 },
  dialog: { borderRadius: 20, padding: 20, width: "100%", maxWidth: 340 },
  dialogTitle: { fontSize: 18, fontWeight: "800" },
  dialogMsg: { fontSize: 15, lineHeight: 21, marginTop: 8 },
  dialogRow: { flexDirection: "row", gap: 10, marginTop: 18 },
  btn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderRadius: 14,
    paddingHorizontal: 18,
    paddingVertical: 12,
    minHeight: 48,
  },
  btnSmall: { minHeight: 40, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 12 },
  iconBtn: { alignItems: "center", justifyContent: "center" },
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 13,
    paddingVertical: 7,
    minHeight: 36,
  },
  chipCount: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 5,
  },
  card: { borderRadius: 18, borderWidth: 1, padding: 16 },
  sectionHeader: { flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 10, marginTop: 4 },
  empty: { alignItems: "center", paddingVertical: 36, paddingHorizontal: 28 },
  emptyIcon: { width: 62, height: 62, borderRadius: 31, alignItems: "center", justifyContent: "center" },
  field: {
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1,
    borderRadius: 14,
    paddingHorizontal: 14,
    minHeight: 48,
  },
  pill: { flexDirection: "row", alignItems: "center", gap: 4, borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3 },
  avatar: { alignItems: "center", justifyContent: "center", overflow: "hidden" },
});

export const useC = (): ThemeColors => useThemeColors(useApp((s) => s.settings.theme));
export const useTT = useT;
