/* Language sheet — 5 languages, native names, scrollable, RTL notice. */

import React, { useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { LANGUAGES, setLang, useT, type Lang } from "../i18n";
import { F, useThemeColors } from "../theme";
import { BottomSheet } from "./Sheet";

export const LanguageSheet: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const language = useApp((s) => s.settings.language);
  const [pick, setPick] = useState<Lang | null>(null);

  const choose = (code: Lang) => {
    if (code === language) {
      onClose();
      return;
    }
    const { rtlChanged } = setLang(code);
    setPick(code);
    if (rtlChanged) {
      Alert.alert(t("rn.restartTitle"), t("rn.restartMsg"), [{ text: t("act.done"), onPress: onClose }]);
    } else {
      onClose();
    }
  };

  return (
    <BottomSheet title={t("st.language")} onClose={onClose}>
      <View style={{ gap: 8, paddingBottom: 8 }}>
        {LANGUAGES.map((l) => {
          const active = (pick ?? language) === l.code;
          return (
            <Pressable
              key={l.code}
              onPress={() => choose(l.code)}
              android_ripple={{ color: "rgba(128,128,128,0.14)" }}
              style={[styles.row, { backgroundColor: active ? C.brandSoft : C.card, borderColor: active ? C.brand : C.line }]}
            >
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: F.h2, fontWeight: "700", color: C.ink }}>{l.native}</Text>
                <Text style={{ fontSize: F.small, color: C.sub }}>
                  {l.label}
                  {l.rtl ? " · RTL" : ""}
                </Text>
              </View>
              {active && <Icon name="check-circle" size={24} color={C.brand} />}
            </Pressable>
          );
        })}
        <Text style={{ fontSize: F.tiny, color: C.faint, paddingHorizontal: 6, paddingTop: 6, lineHeight: 18 }}>
          {t("rn.restartMsg")}
        </Text>
      </View>
    </BottomSheet>
  );
};

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    minHeight: 68,
  },
});
