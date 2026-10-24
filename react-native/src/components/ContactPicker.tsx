/* ContactPicker (owner chooser incl. General) + NumberPicker (which phone). */

import React, { useMemo } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp, contactByRecord } from "../lib/store";
import { useT, t as tr } from "../i18n";
import { copyText, displayName, normalizePhone, prettyPhone, searchContacts } from "../lib/utils";
import { contactOwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Avatar, toast } from "./kit";
import { BottomSheet } from "./Sheet";

export interface OwnerOption {
  ownerId: string;
  label: string;
  recordID?: string;
}

/* v1.6.2 — how many rows render inside the (non-virtualized) sheet list:
   300+ rows made the chooser janky to open; 120 renders instantly and the
   “+N more” hint tells the user to keep typing. */
const RENDER_CAP = 120;

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

  /* scored search — name word-prefix, company, email and phone matching in
     EVERY format (+251…, 09…, plain 9…), ranked best-first (utils v1.6.2) */
  const filtered = q.trim()
    ? searchContacts(contacts, q, displayName, countryCode).map((x) => x.contact)
    : contacts;

  const copyNumber = (number?: string) => {
    if (!number) return;
    copyText(number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"));
  };

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
        {filtered.slice(0, RENDER_CAP).map((c) => (
          <Pressable
            key={c.recordID}
            android_ripple={{ color: "rgba(128,128,128,0.12)" }}
            onPress={() => onPick(contactOwnerId(c.recordID), displayName(c))}
            /* v1.6.2 — long-press copies the first number (user request:
               copy must work wherever a number is shown) */
            onLongPress={() => copyNumber(c.phones[0]?.number)}
            delayLongPress={500}
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
            {/* v1.6.2 — the copy icon WORKS now (user request: "the copy icon
                on the choose contacts and others should work") */}
            {!!c.phones[0] && (
              <Pressable
                onPress={() => copyNumber(c.phones[0].number)}
                hitSlop={6}
                style={({ pressed }) => [styles.copyBtn, { backgroundColor: pressed ? C.chip : C.brandSoft }]}
                accessibilityLabel={`${t("act.copy")} ${c.phones[0].number}`}
              >
                <Icon name="content-copy" size={15} color={C.brand} />
              </Pressable>
            )}
          </Pressable>
        ))}
        {filtered.length > RENDER_CAP && (
          <Text style={{ color: C.faint, textAlign: "center", paddingVertical: 10, fontSize: F.small }}>
            {t("sr.moreResults", { n: filtered.length - RENDER_CAP })}
          </Text>
        )}
        {filtered.length === 0 && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 16, fontSize: F.small }}>
            {t("sr.noResults")}
          </Text>
        )}
      </View>
    </BottomSheet>
  );
};

/** Pick which phone number of a contact to use — the SAME number stored
    in several formats (09…, +251…, plain duplicated rows) appears ONCE. */
export const NumberPicker: React.FC<{
  recordID: string;
  onPick: (number: string) => void;
  onClose: () => void;
  /** v1.5.4 — trailing “＋ Add or edit numbers” row → opens the in-app
      contact editor right from the chooser. */
  onAddEditNumbers?: () => void;
}> = ({ recordID, onPick, onClose, onAddEditNumbers }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === recordID));
  const countryCode = useApp((s) => s.settings.countryCode);

  /* dedupe by normalized digits — the fix for “same number listed over and over” */
  const unique = useMemo(() => {
    const seen = new Set<string>();
    const out: { id: string; label: string; number: string }[] = [];
    (contact?.phones ?? []).forEach((p) => {
      const key = normalizePhone(p.number, countryCode).replace(/\D/g, "") || p.number.replace(/\D/g, "");
      if (seen.has(key)) return;
      seen.add(key);
      out.push(p);
    });
    return out;
  }, [contact, countryCode]);

  return (
    <BottomSheet title={t("cd.chooseNumber")} onClose={onClose}>
      <View style={{ gap: 6, paddingBottom: 10 }}>
        {unique.map((p, i) => (
          <Pressable
            key={p.id || `${p.number}-${i}`}
            android_ripple={{ color: "rgba(128,128,128,0.14)" }}
            onPress={() => onPick(p.number)}
            /* v1.6.1 — long-press a number row to COPY it (user request:
               "long-press copies numbers wherever it is, even in the options") */
            onLongPress={() =>
              copyText(p.number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
            }
            delayLongPress={500}
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
            {/* v1.6.2 — the copy icon is a REAL button now (user request:
               "the copy icon on the choose contacts and others should work") */}
            <Pressable
              onPress={() =>
                copyText(p.number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
              }
              hitSlop={6}
              style={({ pressed }) => [styles.copyBtn, { backgroundColor: pressed ? C.chip : C.brandSoft }]}
              accessibilityLabel={`${t("act.copy")} ${p.number}`}
            >
              <Icon name="content-copy" size={15} color={C.brand} />
            </Pressable>
          </Pressable>
        ))}
        {!unique.length && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 14, fontSize: F.small }}>
            {t("cd.noPhone")}
          </Text>
        )}
        {!!onAddEditNumbers && (
          <Pressable
            android_ripple={{ color: "rgba(128,128,128,0.14)" }}
            onPress={onAddEditNumbers}
            style={[styles.addRow, { borderColor: C.line }]}
            accessibilityLabel={t("cd.addOrEditNumbers")}
          >
            <Icon name="plus" size={16} color={C.brand} />
            <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("cd.addOrEditNumbers")}</Text>
          </Pressable>
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
  /* v1.6.2 — the working copy button used by ContactPicker + NumberPicker */
  copyBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: "center",
    justifyContent: "center",
  },
  addRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: "dashed",
    paddingVertical: 10,
    marginTop: 4,
    minHeight: 44,
  },
});
