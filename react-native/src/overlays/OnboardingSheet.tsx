/* First-run onboarding (user request: "when the app start for the first time
   it should give the option to set the currency and country code").
   Rendered by the shell while settings.onboarded is false — hydrate merges
   {...DEFAULT_SETTINGS, ...stored}, so existing installs see it exactly once.
   "Get started" saves via updateSettings({currency, countryCode, onboarded:
   true}); the back control does the SAME save (defaults are always
   preselected) so the sheet can never trap the user. Later changes live in
   SettingsOverlay (region section) — this sheet is first-run only. */

import React, { useMemo, useState } from "react";
import { Keyboard, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useT } from "../i18n";
import { COUNTRY_CODES, CURRENCIES } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Btn, Field } from "../components/kit";

/* CURRENCIES (types.ts) + the common regional set, so the first-run picker
   covers East-Africa / Middle-East / Asia at a glance. Codes outside this
   list still work everywhere — utils.currencySymbol falls back to the code
   itself as the symbol. */
const ONBOARD_CURRENCIES = [
  ...CURRENCIES,
  { code: "EGP", symbol: "E£", label: "Egyptian Pound" },
  { code: "NGN", symbol: "₦", label: "Nigerian Naira" },
  { code: "ZAR", symbol: "R", label: "South African Rand" },
  { code: "TZS", symbol: "TSh", label: "Tanzanian Shilling" },
  { code: "UGX", symbol: "USh", label: "Ugandan Shilling" },
  { code: "SDG", symbol: "SDG", label: "Sudanese Pound" },
  { code: "DJF", symbol: "Fdj", label: "Djiboutian Franc" },
  { code: "SOS", symbol: "ShSo", label: "Somali Shilling" },
  { code: "CNY", symbol: "¥", label: "Chinese Yuan" },
  { code: "TRY", symbol: "₺", label: "Turkish Lira" },
];

/** Free-text "Other" code: A–Z only, max 3 letters, uppercased. */
const otherCode = (v: string): string => v.trim().toUpperCase().replace(/[^A-Z]/g, "").slice(0, 3);

export const OnboardingSheet: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const insets = useSafeAreaInsets();
  const savedCurrency = useApp((s) => s.settings.currency);
  const savedCountry = useApp((s) => s.settings.countryCode);
  const updateSettings = useApp((s) => s.updateSettings);

  /* defaults preselected from current settings (ET/ETB out of the box) */
  const initialCurrency = savedCurrency || "ETB";
  const initiallyKnown = ONBOARD_CURRENCIES.some((c) => c.code === initialCurrency);
  const [currency, setCurrency] = useState<string>(initiallyKnown ? initialCurrency : "ETB");
  const [other, setOther] = useState<boolean>(!initiallyKnown);
  const [otherText, setOtherText] = useState<string>(initiallyKnown ? "" : initialCurrency);
  const [countryCode, setCountryCode] = useState<string>(
    COUNTRY_CODES.some((c) => c.code === savedCountry) ? savedCountry || "ET" : "ET"
  );
  const [countryQuery, setCountryQuery] = useState("");

  const countries = useMemo(() => {
    const q = countryQuery.trim().toLowerCase();
    if (!q) return COUNTRY_CODES;
    return COUNTRY_CODES.filter(
      (c) => c.name.toLowerCase().includes(q) || c.dial.startsWith(q.replace("+", ""))
    );
  }, [countryQuery]);

  /* saving is ALWAYS allowed — every control has a default, and an invalid
     free-text code falls back to the currency default (ETB).
     v1.6.2: the keyboard is dismissed FIRST — after picking a country the
     search keyboard used to cover the footer's Get-started button (the
     "big btn cropped from bottom" report). */
  const save = () => {
    Keyboard.dismiss();
    const code = other ? otherCode(otherText) || "ETB" : currency;
    updateSettings({ currency: code, countryCode, onboarded: true });
  };

  return (
    /* v1.6.5 — DEDICATED FULL-SCREEN modal (the "Get started cropped from
       bottom" report hit THREE builds while this lived in the generic Sheet):
       the button is no longer a sheet footer — it is pinned in its own
       container under a flex:1 ScrollView, with a hard 36px bottom floor
       plus the real safe-area inset. Nothing above it can squeeze it, no
       keyboard can cover it (adjustResize + dismiss on scroll/tap), and the
       same button also sits at the end of the scroll content. */
    <Modal animationType="slide" visible onRequestClose={save}>
      <View style={[obStyles.root, { backgroundColor: C.canvas, paddingTop: insets.top }]}>
        {/* header — back chevron ALSO saves (defaults are preselected, so
            the sheet can never trap the user) */}
        <View style={[obStyles.header, { borderBottomColor: C.line }]}>
          <Pressable onPress={save} hitSlop={{ top: 12, bottom: 12, left: 8, right: 12 }} style={obStyles.backBtn} accessibilityRole="button" accessibilityLabel={t("act.back")}>
            <Icon name="chevron-left" size={26} color={C.ink} />
          </Pressable>
          <View style={{ flex: 1, alignItems: "center", minWidth: 0 }}>
            <Text style={{ fontSize: F.h2, fontWeight: "800", color: C.ink }} numberOfLines={1}>
              {t("ob.title")}
            </Text>
            <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
              {t("hdr.tagline")}
            </Text>
          </View>
          <View style={{ width: 38 }} />
        </View>

        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{ padding: 18, paddingBottom: 24 }}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode={"on-drag"}
        >
      {/* welcome — brand logo tile, matching the splash look */}
      <View style={{ alignItems: "center", marginBottom: 18 }}>
        <View style={[obStyles.logo, { backgroundColor: C.brand }]}>
          <Icon name="phone-in-talk" size={38} color={C.onBrand} />
        </View>
        <Text style={{ fontSize: F.title, fontWeight: "800", color: C.ink, marginTop: 12 }}>
          {t("ob.welcome")}
        </Text>
        <Text style={{ fontSize: F.small, color: C.sub, marginTop: 4, textAlign: "center" }}>
          {t("ob.hint")}
        </Text>
      </View>

      {/* currency — selectable pills (selected = brand border + brandSoft bg) */}
      <Text style={[obStyles.label, { color: C.sub }]}>{t("st.currency")}</Text>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
        {ONBOARD_CURRENCIES.map((c) => {
          const sel = !other && currency === c.code;
          return (
            <Pressable
              key={c.code}
              onPress={() => {
                setOther(false);
                setCurrency(c.code);
              }}
              accessibilityRole="button"
              accessibilityLabel={c.label}
              style={[
                obStyles.pill,
                { borderColor: sel ? C.brand : C.line, backgroundColor: sel ? C.brandSoft : "transparent" },
              ]}
            >
              <Text style={{ fontSize: F.small, color: sel ? C.brand : C.sub, fontWeight: "700" }}>
                {c.symbol} {c.code}
              </Text>
            </Pressable>
          );
        })}
        <Pressable
          onPress={() => setOther(true)}
          accessibilityRole="button"
          accessibilityLabel={t("ob.other")}
          style={[
            obStyles.pill,
            { borderColor: other ? C.brand : C.line, backgroundColor: other ? C.brandSoft : "transparent" },
          ]}
        >
          <Text style={{ fontSize: F.small, color: other ? C.brand : C.sub, fontWeight: "700" }}>
            {t("ob.other")}
          </Text>
        </Pressable>
      </View>
      {other && (
        <Field
          value={otherText}
          onChangeText={(v) => setOtherText(otherCode(v))}
          placeholder={t("ob.otherPh")}
          autoFocus
          style={{ marginTop: 4 }}
        />
      )}

      {/* country code — compact "Ethiopia +251" rows with search */}
      <Text style={[obStyles.label, { color: C.sub, marginTop: 10 }]}>{t("st.countryCode")}</Text>
      <Field
        value={countryQuery}
        onChangeText={setCountryQuery}
        placeholder={t("act.search")}
        onSubmitEditing={() => Keyboard.dismiss()}
      />
      <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 8 }}>{t("st.countryCodeHint")}</Text>
      <View>
        {countries.map((c) => {
          const sel = countryCode === c.code;
          return (
            <Pressable
              key={c.code}
              onPress={() => {
                /* v1.6.2 — picking a country closes the keyboard: the search
                   keyboard no longer sits over the Save button afterwards */
                Keyboard.dismiss();
                setCountryCode(c.code);
              }}
              android_ripple={{ color: "rgba(128,128,128,0.12)" }}
              accessibilityRole="button"
              accessibilityLabel={`${c.name} +${c.dial}`}
              style={[
                obStyles.countryRow,
                { borderColor: sel ? C.brand : C.line, backgroundColor: sel ? C.brandSoft : "transparent" },
              ]}
            >
              <Text style={{ flex: 1, fontSize: F.small, color: C.ink, fontWeight: "600" }} numberOfLines={1}>
                {c.name}
              </Text>
              <Text style={{ fontSize: F.small, color: sel ? C.brand : C.sub, fontWeight: "700" }}>+{c.dial}</Text>
              {sel && <Icon name="check-circle" size={18} color={C.brand} />}
            </Pressable>
          );
        })}
        {countries.length === 0 && (
          <Text style={{ fontSize: F.small, color: C.faint, paddingVertical: 10, textAlign: "center" }}>
            {t("lg.noMatch")}
          </Text>
        )}
      </View>

      {/* in-content Get-started at the very END of the scroll — always
          reachable by scrolling, even if something exotic covers the pinned
          bottom button. */}
      <Btn
        label={t("ob.getStarted")}
        icon="arrow-right"
        onPress={save}
        style={{ marginTop: 18, marginBottom: 12, minHeight: 58 }}
      />
        </ScrollView>

        {/* THE big button — pinned outside the scroll, hard floor under it */}
        <View style={[obStyles.footer, { borderTopColor: C.line, paddingBottom: Math.max(insets.bottom, 36) }]}>
          <Btn label={t("ob.getStarted")} icon="arrow-right" onPress={save} style={{ flex: 1, minHeight: 58 }} />
        </View>
      </View>
    </Modal>
  );
};

const obStyles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 10,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  backBtn: { width: 38, height: 44, alignItems: "center", justifyContent: "center" },
  footer: { borderTopWidth: StyleSheet.hairlineWidth, paddingHorizontal: 16, paddingTop: 12 },
  logo: { width: 74, height: 74, borderRadius: 24, alignItems: "center", justifyContent: "center" },
  label: { fontSize: F.small, fontWeight: "600", marginBottom: 8 },
  pill: {
    /* same pill style as the SettingsOverlay region pickers */
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 7,
    minHeight: 36,
    justifyContent: "center",
  },
  countryRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingHorizontal: 12,
    paddingVertical: 9,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 6,
    minHeight: 44,
  },
});
