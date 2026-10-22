/* ListsSection — calculating / checklist / todo / simple lists + templates. */

import React, { useMemo, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { listTotals } from "../lib/calc";
import { relTime } from "../lib/utils";
import type { CalcList, ListKind, OwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Btn, Card, EmptyState, Money, Pill, SectionHeader, confirm, toast, toastUndo } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

const KIND_ICON: Record<ListKind, string> = {
  calculating: "calculator-variant-outline",
  simple: "format-list-bulleted",
  checklist: "check-bold",
  todo: "clipboard-check-outline",
};

export const ListsSection: React.FC<{ ownerId: OwnerId }> = ({ ownerId }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const lists = useApp((s) => s.lists);
  const deleteList = useApp((s) => s.deleteList);
  const duplicateList = useApp((s) => s.duplicateList);
  const [kindPicker, setKindPicker] = useState(false);

  const mine = useMemo(
    () => lists.filter((l) => l.ownerId === ownerId && !l.deletedAt && !l.isTemplate).sort((a, b) => b.updatedAt - a.updatedAt),
    [lists, ownerId]
  );
  const templates = useMemo(
    () => lists.filter((l) => l.ownerId === ownerId && !l.deletedAt && l.isTemplate),
    [lists, ownerId]
  );

  const menuFor = (l: CalcList) => {
    Alert.alert(l.title, undefined, [
      { text: t("act.open"), onPress: () => open({ type: "list", listId: l.id }) },
      { text: t("act.duplicate"), onPress: () => duplicateList(l.id) },
      { text: t("ls.saveAsTemplate"), onPress: () => { duplicateList(l.id, { asTemplate: true }); toast(t("ls.savedAsTemplate"), "ok"); } },
      {
        text: t("act.delete"),
        style: "destructive",
        onPress: async () => {
          const ok = await confirm(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), true);
          if (ok) {
            deleteList(l.id);
            toastUndo();
          }
        },
      },
      { text: t("act.cancel"), style: "cancel" },
    ]);
  };

  return (
    <View>
      <View style={{ flexDirection: "row", gap: 6, marginBottom: 10 }}>
        <Btn small label={t("ls.newList")} icon="plus" onPress={() => setKindPicker(true)} />
      </View>

      {mine.length === 0 && (
        <EmptyState
          icon="clipboard-list-outline"
          title={t("ls.noLists")}
          hint={t("ls.listsHint")}
          actionLabel={t("ls.newList")}
          onAction={() => setKindPicker(true)}
        />
      )}

      {mine.map((l) => {
        const totals = listTotals(l);
        const isCalc = l.kind === "calculating";
        return (
          <View key={l.id} style={{ marginBottom: 8 }}>
            <SwipeRow
              onTap={() => open({ type: "list", listId: l.id })}
              onLongPress={() => menuFor(l)}
              leftActions={[{ icon: "open-in-new", color: C.brand, onPress: () => open({ type: "list", listId: l.id }) }]}
              rightActions={[
                {
                  icon: "content-duplicate",
                  color: "#4a7d9b",
                  onPress: () => {
                    duplicateList(l.id);
                    toast(t("ls.listDuplicated"), "ok");
                  },
                },
                {
                  icon: "trash-can-outline",
                  color: C.red,
                  onPress: async () => {
                    const ok = await confirm(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), true);
                    if (ok) {
                      deleteList(l.id);
                      toastUndo();
                    }
                  },
                },
              ]}
            >
              <Card>
                <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                  <View style={[listStyles.icon, { backgroundColor: C.brandSoft }]}>
                    <Icon name={KIND_ICON[l.kind]} size={19} color={C.brand} />
                  </View>
                  <View style={{ flex: 1, gap: 2 }}>
                    <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                      {l.title}
                    </Text>
                    <Text style={{ fontSize: F.tiny, color: C.sub }}>
                      {totals.count} {t("ls.items")} · {relTime(l.updatedAt)}
                      {l.repeat !== "none" ? ` · ${t(`rm.repeat${l.repeat.charAt(0).toUpperCase()}${l.repeat.slice(1)}`)}` : ""}
                    </Text>
                  </View>
                  {isCalc ? (
                    <View style={{ alignItems: "flex-end", gap: 2 }}>
                      <Money value={totals.grand} size={15} weight="800" />
                      {totals.pending > 0.005 ? (
                        <Pill label={`${totals.count - totals.paidCount}/${totals.count}`} tone="amber" />
                      ) : (
                        <Pill label={t("ls.allSettled")} tone="green" icon="check" />
                      )}
                    </View>
                  ) : (
                    <Pill label={`${totals.paidCount}/${totals.count}`} tone={totals.paidCount === totals.count ? "green" : "neutral"} />
                  )}
                  <Icon name="chevron-right" size={20} color={C.faint} />
                </View>
                {l.postToLedger && (
                  <View style={{ marginTop: 8 }}>
                    <Pill label={t("ls.countPending")} tone="amber" icon="scale-balance" />
                  </View>
                )}
              </Card>
            </SwipeRow>
          </View>
        );
      })}

      {/* templates */}
      {templates.length > 0 && (
        <View style={{ marginTop: 10 }}>
          <SectionHeader title={t("ls.templates")} subtitle={t("ls.reuseTemplate")} />
          {templates.map((tpl) => (
            <Card key={tpl.id} style={{ marginBottom: 8, borderStyle: "dashed" as "solid" | "dotted" | "dashed" }}>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                <Icon name={KIND_ICON[tpl.kind]} size={19} color={C.sub} />
                <Text style={{ flex: 1, fontSize: F.small, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                  {tpl.title}
                </Text>
                <Pill label={t("ls.templates")} tone="neutral" />
                <Btn
                  small
                  label={t("act.new")}
                  onPress={() => {
                    const id = duplicateList(tpl.id, { fresh: true });
                    if (id) open({ type: "list", listId: id });
                  }}
                />
              </View>
            </Card>
          ))}
        </View>
      )}

      {/* kind picker */}
      {kindPicker && (
        <BottomSheet title={t("ls.newList")} onClose={() => setKindPicker(false)}>
          <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 12 }}>{t("ls.listsHint")}</Text>
          {(["calculating", "checklist", "todo", "simple"] as ListKind[]).map((k) => (
            <Pressable
              key={k}
              android_ripple={{ color: "rgba(128,128,128,0.12)" }}
              onPress={() => {
                setKindPicker(false);
                open({ type: "listEdit", ownerId, kind: k });
              }}
              style={[listStyles.kindRow, { borderColor: C.line, backgroundColor: C.card }]}
            >
              <View style={[listStyles.icon, { backgroundColor: C.brandSoft }]}>
                <Icon name={KIND_ICON[k]} size={20} color={C.brand} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }}>
                  {t(`ls.kind${k.charAt(0).toUpperCase()}${k.slice(1)}`)}
                </Text>
                <Text style={{ fontSize: F.tiny, color: C.sub }}>
                  {k === "calculating" ? t("ls.totalsCharges") : k === "checklist" ? t("ls.groupSameItems") : k === "todo" ? t("ls.priority") : t("ls.items")}
                </Text>
              </View>
              <Icon name="chevron-right" size={20} color={C.faint} />
            </Pressable>
          ))}
        </BottomSheet>
      )}
    </View>
  );
};

const listStyles = StyleSheet.create({
  icon: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  kindRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 8,
    minHeight: 66,
  },
});
