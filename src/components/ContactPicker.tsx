/* ContactPicker (owner chooser incl. General) + NumberPicker (which phone). */

import React from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { useApp, contactByRecord } from "../lib/store";
import { useT, t as tr } from "../i18n";
import { displayName, prettyPhone } from "../lib/utils";
import { contactOwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Avatar } from "./kit";
import { BottomSheet } from "./Sheet";

export interface OwnerOption {
  ownerId: string;
  label: string;
  recordID?: string;
}

/** Pick an owner: General + all device contacts (searchable). */
export const ContactPicker: React.FC<{
  onPick: (ownerId: string, label: string) => void;
  onClose: () => void;
  title?: string;
  includeGeneral?: boolean;
}> = ({ onPick, onClose, title, includeGeneral = true }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const contacts = useApp((s) => s.deviceContacts);
  const countryCode = useApp((s) => s.settings.countryCode);
  const [q, setQ] = React.useState("");

  const filtered = q.trim()
    ? contacts.filter(
        (c) =>
          displayName(c).toLowerCase().includes(q.toLowerCase()) ||
          c.phones.some((p) => p.number.replace(/\D/g, "").includes(q.replace(/\D/g, "")))
      )
    : contacts;

  return (
    <BottomSheet title={title || t("spd.chooseContact")} onClose={onClose}>
      <View style={{ marginBottom: 10 }}>
        <TextInput
          value={q}
          onChangeText={setQ}
          placeholder={t("misc.searchPlaceholder")}
          placeholderTextColor={C.faint}
          style={[styles.search, { borderColor: C.line, backgroundColor: C.inputBg, color: C.ink }]}
        />
      </View>
      {includeGeneral && (
        <Pressable
          android_ripple={{ color: "rgba(128,128,128,0.14)" }}
          onPress={() => onPick("general", t("misc.general"))}
          style={[styles.row, { borderColor: C.line, backgroundColor: C.cardAlt }]}
        >
          <View style={[styles.genIcon, { backgroundColor: C.brand }]}>
            <Text style={{ color: C.onBrand, fontWeight: "800", fontSize: 16 }}>G</Text>
          </View>
          <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }}>{t("misc.general")}</Text>
        </Pressable>
      )}
      <View style={{ gap: 4, paddingBottom: 10 }}>
        {filtered.slice(0, 300).map((c) => (
          <Pressable
            key={c.recordID}
            android_ripple={{ color: "rgba(128,128,128,0.12)" }}
            onPress={() => onPick(contactOwnerId(c.recordID), displayName(c))}
            style={({ pressed }) => [styles.row, { backgroundColor: pressed ? C.chip : "transparent" }]}
          >
            <Avatar contact={c} size={36} />
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                {displayName(c)}
              </Text>
              {!!c.phones[0] && (
                <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                  {prettyPhone(c.phones[0].number, countryCode)}
                </Text>
              )}
            </View>
          </Pressable>
        ))}
        {filtered.length === 0 && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 16, fontSize: F.small }}>
            {t("sr.noResults")}
          </Text>
        )}
      </View>
    </BottomSheet>
  );
};

/** Pick which phone number of a contact to use. */
export const NumberPicker: React.FC<{
  recordID: string;
  onPick: (number: string) => void;
  onClose: () => void;
}> = ({ recordID, onPick, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === recordID));
  const countryCode = useApp((s) => s.settings.countryCode);

  return (
    <BottomSheet title={t("cd.chooseNumber")} onClose={onClose}>
      <View style={{ gap: 6, paddingBottom: 10 }}>
        {(contact?.phones ?? []).map((p, i) => (
          <Pressable
            key={p.id || i}
            android_ripple={{ color: "rgba(128,128,128,0.14)" }}
            onPress={() => onPick(p.number)}
            style={[styles.row, { borderColor: C.line, backgroundColor: C.card }]}
          >
            <View style={{ flex: 1 }}>
              <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }}>
                {prettyPhone(p.number, countryCode)}
              </Text>
              <Text style={{ fontSize: F.tiny, color: C.sub }}>
                {t(`cd.${p.label === "other" ? "other" : p.label}`) !== `cd.${p.label}` ? t(`cd.${p.label}`) : p.label}
              </Text>
            </View>
          </Pressable>
        ))}
        {!contact?.phones.length && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 14, fontSize: F.small }}>
            {t("cd.noPhone")}
          </Text>
        )}
      </View>
    </BottomSheet>
  );
};

export const useOwnerName = (ownerId: string): string => {
  const t = useT();
  const contact = useApp((s) =>
    ownerId.startsWith("c:") ? s.deviceContacts.find((c) => `c:${c.recordID}` === ownerId) : undefined
  );
  if (ownerId === "general") return t("misc.general");
  return contact ? displayName(contact) : t("cd.notFound");
};

export const ownerLabel = (ownerId: string): string => {
  const contact = contactByRecord(ownerId.replace(/^c:/, ""));
  if (ownerId === "general") return tr("misc.general");
  return contact ? displayName(contact) : "—";
};

const styles = StyleSheet.create({
  search: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 14, height: 46, fontSize: F.body },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    minHeight: 60,
  },
  genIcon: { width: 36, height: 36, borderRadius: 18, alignItems: "center", justifyContent: "center" },
});
