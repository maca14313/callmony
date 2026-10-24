/* ListDetail + ListEdit — calculating lists with qty×price items,
   tick-payments, totals & charges, share / remind / mark-all-paid. */

import React, { useMemo, useState } from "react";
import { Alert, Pressable, StyleSheet, Switch, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { groupItems, itemPaid, itemRemaining, lineTotal, listTotals } from "../lib/calc";
import { fd, money, todayStr } from "../lib/utils";
import type { ListItem, OwnerId, Repeat } from "../lib/types";
import { listShareText } from "../lib/exporters";
import { openURL, shareText, waLink } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Chip, Divider, EmptyState, Field, IconBtn, Money, Pill, SectionHeader, Segmented, confirm, toast, toastUndo } from "../components/kit";
import { Sheet, BottomSheet } from "../components/Sheet";
import { ContactPicker, useOwnerName } from "../components/ContactPicker";

/* ================= ListDetail ================= */

export const ListDetailOverlay: React.FC<{ params: Extract<Overlay, { type: "list" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const lists = useApp((s) => s.lists);
  const saveList = useApp((s) => s.saveList);
  const addListItem = useApp((s) => s.addListItem);
  const markAllPaid = useApp((s) => s.markAllPaid);
  const clearPaid = useApp((s) => s.clearPaid);
  const duplicateList = useApp((s) => s.duplicateList);
  const deleteList = useApp((s) => s.deleteList);
  const saveReminder = useApp((s) => s.saveReminder);
  const logMessage = useApp((s) => s.logMessage);

  const list = lists.find((l) => l.id === params.listId && !l.deletedAt);
  const ownerName = useOwnerName(list?.ownerId ?? "general");
  const [groupSame, setGroupSame] = useState(false);
  const [expandedItem, setExpandedItem] = useState<string | null>(null);
  const [totalsSheet, setTotalsSheet] = useState(false);
  const [pickOwner, setPickOwner] = useState(false);
  const [iName, setIName] = useState("");
  const [iQty, setIQty] = useState("1");
  const [iUnit, setIUnit] = useState("");
  const [iPrice, setIPrice] = useState("");
  const [dType, setDType] = useState<"pct" | "amount">("pct");
  const [dValue, setDValue] = useState("");
  const [tax, setTax] = useState("");
  const [extra, setExtra] = useState("");

  /* hooks MUST run before the not-found early return (React contract) */
  const isCalc = list?.kind === "calculating";
  const rows: { key: string; items: ListItem[] }[] = useMemo(() => {
    if (!list || !isCalc || !groupSame) return [{ key: "all", items: list?.items ?? [] }];
    return groupItems(list.items).map((g, i) => ({ key: `g${i}`, items: g }));
  }, [list, groupSame, isCalc]);

  if (!list) {
    return (
      <Sheet title={t("ls.listNotFound")} subtitle={t("ls.listNotFoundHint")} onBack={pop}>
        <EmptyState icon="clipboard-off-outline" title={t("ls.listNotFound")} hint={t("ls.listNotFoundHint")} />
      </Sheet>
    );
  }

  const totals = listTotals(list);

  const menu = () => {
    Alert.alert(list.title, undefined, [
      {
        text: t("ls.renameEdit"),
        onPress: () => useUI.getState().open({ type: "listEdit", listId: list.id, ownerId: list.ownerId }),
      },
      { text: t("act.duplicate"), onPress: () => { duplicateList(list.id); toast(t("ls.listDuplicated"), "ok"); } },
      { text: t("ls.saveAsTemplate"), onPress: () => { duplicateList(list.id, { asTemplate: true }); toast(t("ls.savedAsTemplate"), "ok"); } },
      { text: t("ls.markAllPaid"), onPress: () => { markAllPaid(list.id); toast(t("ls.allPaid"), "ok"); } },
      {
        text: t("ls.clearPayments"),
        onPress: async () => {
          const ok = await confirm(t("ls.clearPayments"), t("ls.paymentsCleared"), t("act.done"), true);
          if (ok) clearPaid(list.id);
        },
      },
      {
        text: t("act.delete"),
        style: "destructive",
        onPress: async () => {
          const ok = await confirm(t("ls.deleteListTitle"), t("ls.deleteListMsg"), t("act.delete"), true);
          if (ok) {
            deleteList(list.id);
            toastUndo();
            pop();
          }
        },
      },
      { text: t("act.cancel"), style: "cancel" },
    ]);
  };

  const share = async (channel: "share" | "whatsapp") => {
    const text = listShareText(list);
    if (channel === "whatsapp") {
      const phone = list.ownerId.startsWith("c:")
        ? useApp.getState().deviceContacts.find((c) => `c:${c.recordID}` === list.ownerId)?.phones[0]?.number
        : undefined;
      if (phone) {
        logMessage({ ownerId: list.ownerId, channel: "whatsapp", text });
        openURL(waLink(phone, text, useApp.getState().settings.countryCode));
        return;
      }
    }
    const ok = await shareText(list.title, text);
    if (ok) logMessage({ ownerId: list.ownerId, channel: "other", text });
  };

  const remind = () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    tomorrow.setHours(9, 0, 0, 0);
    saveReminder({
      ownerId: list.ownerId,
      title: `${t("ls.remind")}: ${list.title}`,
      kind: "payment",
      dueAt: tomorrow.getTime(),
      notes: `${t("lg.pending")}: ${money(totals.pending)}`,
    });
    toast(t("ls.remindSet"), "ok");
  };

  const addItem = () => {
    const name = iName.trim();
    const qty = Number(iQty) || 0;
    const price = Number(iPrice) || 0;
    if (!name || qty <= 0) {
      toast(t("ls.giveItemName"), "error");
      return;
    }
    addListItem(list.id, { name, qty, unit: iUnit.trim() || undefined, unitPrice: price });
    setIName("");
    setIQty("1");
    setIUnit("");
    setIPrice("");
    toast(t("ls.itemAdded"), "ok");
  };

  return (
    <Sheet
      title={list.title}
      subtitle={`${t(`ls.kind${list.kind.charAt(0).toUpperCase()}${list.kind.slice(1)}`)} · ${ownerName}`}
      onBack={pop}
      headerRight={<IconBtn icon="dots-vertical" size={34} onPress={menu} />}
      footer={
        <View style={{ flexDirection: "row", gap: 8 }}>
          <Btn small label={t("act.whatsapp")} icon="whatsapp" variant="soft" onPress={() => share("whatsapp")} style={{ flex: 1 }} />
          <Btn small label={t("act.share")} icon="share-variant-outline" variant="secondary" onPress={() => share("share")} style={{ flex: 1 }} />
          <Btn small label={t("ls.remind")} icon="bell-outline" variant="secondary" onPress={remind} style={{ flex: 0.6 }} />
        </View>
      }
    >
      {/* sub-header card */}
      <Card style={{ marginBottom: 10 }}>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <Pressable onPress={() => setPickOwner(true)}>
            <Pill label={ownerName} tone="brand" icon="account-circle-outline" />
          </Pressable>
          {list.repeat !== "none" && (
            <Pill label={t(`rm.repeat${list.repeat.charAt(0).toUpperCase()}${list.repeat.slice(1)}`)} tone="blue" icon="repeat" />
          )}
          {list.isTemplate && <Pill label={t("ls.templates")} tone="neutral" />}
          {list.postToLedger && <Pill label={t("ls.countPending")} tone="amber" icon="scale-balance" />}
        </View>
        {list.note ? (
          <Text style={{ fontSize: F.small, color: C.sub, marginTop: 8 }}>{list.note}</Text>
        ) : null}
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginTop: 10 }}>
          <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{t("ls.countPendingHint")}</Text>
          <Switch
            value={list.postToLedger}
            onValueChange={(v) => saveList({ id: list.id, ownerId: list.ownerId, postToLedger: v })}
            trackColor={{ true: C.brand, false: C.line }}
            thumbColor="#fff"
          />
        </View>
        {list.postToLedger && (
          <Text style={{ fontSize: F.tiny, color: C.amber, marginTop: 4 }}>
            {t("ls.pendingHint")}: {money(totals.pending)}
          </Text>
        )}
        {list.repeat !== "none" && (
          <Btn
            small
            label={t("ls.duplicatedNext")}
            icon="calendar-refresh-outline"
            variant="soft"
            style={{ marginTop: 10, alignSelf: "flex-start" }}
            onPress={() => {
              const id = duplicateList(list.id, { fresh: true });
              if (id) useUI.getState().open({ type: "list", listId: id });
              toast(t("ls.listDuplicated"), "ok");
            }}
          />
        )}
      </Card>

      {/* totals & charges (calculating only) */}
      {isCalc && (
        <Pressable onPress={() => setTotalsSheet(true)}>
          <Card style={{ marginBottom: 10 }}>
            <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
              <Icon name="sigma" size={20} color={C.brand} />
              <Text style={{ flex: 1, fontSize: F.small, color: C.sub, fontWeight: "700" }}>
                {t("ls.totalsCharges")} · {t("ls.totalsTapToEdit")}
              </Text>
              <Icon name="pencil-outline" size={16} color={C.faint} />
            </View>
            <View style={{ marginTop: 8, gap: 3 }}>
              <TotalRow label={t("ls.subtotal")} value={totals.subtotal} C={C} />
              {totals.listDiscount > 0 && <TotalRow label={t("ls.listDiscount")} value={-totals.listDiscount} C={C} />}
              {totals.listTax > 0 && <TotalRow label={t("ls.taxPct")} value={totals.listTax} C={C} />}
              {totals.extra > 0 && <TotalRow label={t("ls.extraCharge")} value={totals.extra} C={C} />}
              <View style={{ flexDirection: "row", justifyContent: "space-between", marginTop: 4 }}>
                <Text style={{ fontSize: F.body, fontWeight: "800", color: C.ink }}>{t("ls.total")}</Text>
                <Money value={totals.grand} size={17} weight="800" />
              </View>
              <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                <Text style={{ fontSize: F.small, color: C.sub }}>{t("lg.paid")}</Text>
                <Text style={{ fontSize: F.small, color: C.green, fontWeight: "700" }}>{money(totals.paid)}</Text>
              </View>
              <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
                <Text style={{ fontSize: F.small, color: C.sub }}>{t("lg.pending")}</Text>
                <Text style={{ fontSize: F.small, color: totals.pending > 0 ? C.amber : C.green, fontWeight: "700" }}>
                  {money(totals.pending)}
                </Text>
              </View>
            </View>
          </Card>
        </Pressable>
      )}

      {/* group-same switch */}
      {isCalc && list.items.length > 1 && (
        <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 10 }}>
          <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{t("ls.groupSameItems")}</Text>
          <Switch
            value={groupSame}
            onValueChange={setGroupSame}
            trackColor={{ true: C.brand, false: C.line }}
            thumbColor="#fff"
          />
        </View>
      )}

      {/* items */}
      {rows.map((row) => (
        <View key={row.key}>
          {row.items.length > 1 && (
            <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700", marginBottom: 4 }}>
              {row.items[0].name} · {row.items.length} × {t("ls.qty")} {row.items.reduce((s, i) => s + i.qty, 0)}
            </Text>
          )}
          {row.items.map((item) => (
            <CalcItemRow
              key={item.id}
              listId={list.id}
              item={item}
              isCalc={isCalc}
              expanded={expandedItem === item.id}
              onToggleExpand={() => setExpandedItem(expandedItem === item.id ? null : item.id)}
            />
          ))}
        </View>
      ))}

      {list.items.length === 0 && (
        <EmptyState icon="playlist-plus" title={t("ls.noItems")} hint={`${t("ls.noItemsHintA")} ${t("ls.noItemsHintB")}`} />
      )}

      {/* add item form */}
      <View style={[styles.addBox, { borderColor: C.line, backgroundColor: C.cardAlt }]}>
        <Field label={t("ls.itemName")} value={iName} onChangeText={setIName} placeholder={t("ls.giveItemName")} />
        <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-end" }}>
          <View style={{ flex: 0.7 }}>
            <Field label={t("ls.qty")} value={iQty} onChangeText={setIQty} keyboardType="decimal-pad" placeholder="1" />
          </View>
          <View style={{ flex: 0.8 }}>
            <Field label={t("ls.unit")} value={iUnit} onChangeText={setIUnit} placeholder={t("ls.unitPlaceholder")} />
          </View>
          {isCalc && (
            <View style={{ flex: 1.2 }}>
              <Field label={t("ls.unitPrice")} value={iPrice} onChangeText={setIPrice} keyboardType="decimal-pad" placeholder="0" />
            </View>
          )}
          <Btn label={t("act.add")} icon="plus" onPress={addItem} style={{ marginBottom: 12, minHeight: 48 }} />
        </View>
      </View>

      {/* totals sheet */}
      {totalsSheet && (
        <BottomSheet title={t("ls.totalsCharges")} onClose={() => setTotalsSheet(false)}>
          <View style={{ flexDirection: "row", gap: 8, marginBottom: 10 }}>
            <Chip label="%" active={dType === "pct"} onPress={() => setDType("pct")} />
            <Chip label={money(1).split(" ")[1] || "Br"} active={dType === "amount"} onPress={() => setDType("amount")} />
            <View style={{ flex: 1 }}>
              <Field label={t("ls.listDiscount")} value={dValue} onChangeText={setDValue} keyboardType="decimal-pad" placeholder="0" />
            </View>
          </View>
          <Field label={t("ls.taxPercent")} value={tax} onChangeText={setTax} keyboardType="decimal-pad" placeholder="0" />
          <Field label={t("ls.extraCharge")} value={extra} onChangeText={setExtra} keyboardType="decimal-pad" placeholder="0" />
          <Btn
            label={t("act.done")}
            onPress={() => {
              saveList({
                id: list.id,
                ownerId: list.ownerId,
                discount: Number(dValue) > 0 ? { type: dType, value: Number(dValue) } : undefined,
                taxPct: Number(tax) > 0 ? Number(tax) : undefined,
                extraCharge: Number(extra) > 0 ? Number(extra) : undefined,
              });
              setTotalsSheet(false);
              toast(t("lg.totalsUpdated"), "ok");
            }}
            style={{ marginTop: 6 }}
          />
        </BottomSheet>
      )}

      {/* owner picker */}
      {pickOwner && (
        <ContactPicker
          title={t("ls.owner")}
          onClose={() => setPickOwner(false)}
          onPick={(id) => {
            useApp.getState().moveEntity("list", list.id, id as OwnerId);
            setPickOwner(false);
            toast(t("ls.listMoved"), "ok");
          }}
        />
      )}
    </Sheet>
  );
};

/* ---------------- calc item row ---------------- */

const CalcItemRow: React.FC<{
  listId: string;
  item: ListItem;
  isCalc: boolean;
  expanded: boolean;
  onToggleExpand: () => void;
}> = ({ listId, item, isCalc, expanded, onToggleExpand }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const openCalculator = useUI((s) => s.openCalculator);
  const toggleItemChecked = useApp((s) => s.toggleItemChecked);
  const updateListItem = useApp((s) => s.updateListItem);
  const removeListItem = useApp((s) => s.removeListItem);
  const addItemPayment = useApp((s) => s.addItemPayment);

  const [qty, setQty] = useState(String(item.qty));
  const [unit, setUnit] = useState(item.unit ?? "");
  const [price, setPrice] = useState(String(item.unitPrice));
  // per-row discount / tax editor state
  const [discType, setDiscType] = useState<"pct" | "amount">(item.discount?.type ?? "pct");
  const [discValue, setDiscValue] = useState(item.discount ? String(item.discount.value) : "");
  const [taxStr, setTaxStr] = useState(item.taxPct ? String(item.taxPct) : "");
  // payment sheet state — amount + date + method + note
  const [payAmount, setPayAmount] = useState("");
  const [payDate, setPayDate] = useState(todayStr());
  const [payMethod, setPayMethod] = useState("cash");
  const [payNote, setPayNote] = useState("");
  const [paySheet, setPaySheet] = useState(false);

  const total = lineTotal(item);
  const paid = itemPaid(item);
  const remaining = itemRemaining(item);

  // row discount — 0 clears it (mirrors the totals-sheet chip pattern)
  const commitDiscount = (type: "pct" | "amount", raw: string) => {
    const v = Number(raw) || 0;
    updateListItem(listId, item.id, { discount: v > 0 ? { type, value: v } : undefined });
  };

  return (
    <Card style={{ marginBottom: 8 }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
        <Pressable
          onPress={async () => {
            if (item.checked) {
              const ok = await confirm(t("ls.removeTickTitle"), t("ls.removeTickMsg"), t("act.remove"), true);
              if (ok) toggleItemChecked(listId, item.id);
            } else {
              toggleItemChecked(listId, item.id);
            }
          }}
          android_ripple={{ color: "rgba(128,128,128,0.2)", borderless: true, radius: 22 }}
          style={[rowStyles.tick, { borderColor: item.checked ? C.brand : C.line, backgroundColor: item.checked ? C.brand : "transparent" }]}
          accessibilityLabel={t("act.done")}
        >
          {item.checked && <Icon name="check" size={20} color={C.onBrand} />}
        </Pressable>
        <Pressable style={{ flex: 1 }} onPress={onToggleExpand}>
          <Text
            numberOfLines={1}
            style={{ fontSize: F.body, fontWeight: "700", color: C.ink, textDecorationLine: item.checked ? "line-through" : "none" }}
          >
            {item.name}
          </Text>
          <Text style={{ fontSize: F.tiny, color: C.sub }}>
            {item.qty}
            {item.unit ? ` ${item.unit}` : ""} × {money(item.unitPrice)}
            {item.date ? ` · ${fd(item.date)}` : ""}
            {isCalc && paid > 0 ? ` · ${t("lg.paid")} ${money(paid)}` : ""}
          </Text>
        </Pressable>
        {isCalc && <Money value={total} size={15} weight="700" />}
        <Pressable onPress={onToggleExpand} hitSlop={6}>
          <Icon name={expanded ? "chevron-up" : "chevron-down"} size={20} color={C.faint} />
        </Pressable>
      </View>

      {isCalc && remaining > 0.005 && (
        <View style={{ marginTop: 8, marginLeft: 44 }}>
          <Pill label={`${t("lg.pending")} ${money(remaining)}`} tone="amber" />
        </View>
      )}

      {expanded && (
        <View style={{ marginTop: 8 }}>
          <Divider />
          {/* name — editable */}
          <Field
            label={t("ls.itemName")}
            value={item.name}
            onChangeText={(v) => updateListItem(listId, item.id, { name: v })}
            placeholder={t("ls.giveItemName")}
          />
          <View style={{ flexDirection: "row", gap: 8 }}>
            <View style={{ flex: 0.7 }}>
              <Field
                label={t("ls.qty")}
                value={qty}
                onChangeText={(v) => {
                  setQty(v);
                  updateListItem(listId, item.id, { qty: Number(v) || 0 });
                }}
                keyboardType="decimal-pad"
              />
            </View>
            <View style={{ flex: 0.9 }}>
              <Field
                label={t("ls.unit")}
                value={unit}
                onChangeText={(v) => {
                  setUnit(v);
                  updateListItem(listId, item.id, { unit: v.trim() || undefined });
                }}
                placeholder={t("ls.unitPlaceholder")}
              />
            </View>
            <View style={{ flex: 1.2 }}>
              <Field
                label={t("ls.unitPrice")}
                value={price}
                onChangeText={(v) => {
                  setPrice(v);
                  updateListItem(listId, item.id, { unitPrice: Number(v) || 0 });
                }}
                keyboardType="decimal-pad"
                right={
                  <IconBtn
                    icon="calculator"
                    size={32}
                    onPress={() =>
                      openCalculator((v) => {
                        setPrice(String(v));
                        updateListItem(listId, item.id, { unitPrice: Number(v) || 0 });
                      })
                    }
                  />
                }
              />
            </View>
          </View>
          {/* per-row date + due date */}
          <View style={{ flexDirection: "row", gap: 8 }}>
            <View style={{ flex: 1 }}>
              <Field
                label={t("ls.rowDate")}
                value={item.date ?? ""}
                onChangeText={(v) => updateListItem(listId, item.id, { date: v || undefined })}
                placeholder="YYYY-MM-DD"
              />
            </View>
            <View style={{ flex: 1 }}>
              <Field
                label={t("ls.itemDueDate")}
                value={item.dueDate ?? ""}
                onChangeText={(v) => updateListItem(listId, item.id, { dueDate: v || undefined })}
                placeholder="YYYY-MM-DD"
              />
            </View>
          </View>
          {/* per-row note */}
          <Field
            label={t("ls.itemNote")}
            value={item.note ?? ""}
            onChangeText={(v) => updateListItem(listId, item.id, { note: v || undefined })}
            placeholder={t("lg.optionalNote")}
          />
          {/* per-row discount + tax (calculating lists) */}
          {isCalc && (
            <>
              <View style={{ flexDirection: "row", gap: 6 }}>
                <Chip label="%" active={discType === "pct"} onPress={() => { setDiscType("pct"); commitDiscount("pct", discValue); }} />
                <Chip label={money(1).split(" ")[1] || "Br"} active={discType === "amount"} onPress={() => { setDiscType("amount"); commitDiscount("amount", discValue); }} />
                <View style={{ flex: 1 }}>
                  <Field
                    label={t("ls.rowDiscount")}
                    value={discValue}
                    onChangeText={(v) => {
                      setDiscValue(v);
                      commitDiscount(discType, v);
                    }}
                    keyboardType="decimal-pad"
                    placeholder="0"
                  />
                </View>
              </View>
              <Field
                label={t("ls.taxPct")}
                value={taxStr}
                onChangeText={(v) => {
                  setTaxStr(v);
                  const n = Number(v) || 0;
                  updateListItem(listId, item.id, { taxPct: n > 0 ? n : undefined });
                }}
                keyboardType="decimal-pad"
                placeholder="0"
              />
            </>
          )}
          <View style={{ flexDirection: "row", gap: 6, flexWrap: "wrap" }}>
            {(["low", "medium", "high"] as const).map((p) => (
              <Chip key={p} label={t(`ls.${p}`)} active={item.priority === p} onPress={() => updateListItem(listId, item.id, { priority: p })} />
            ))}
            <Chip
              label={t("lg.addPayment")}
              icon="plus"
              onPress={() => setPaySheet(true)}
            />
            <Chip
              label={t("ls.markAllPaid")}
              icon="check-all"
              onPress={() => {
                if (remaining > 0.005) addItemPayment(listId, item.id, { amount: remaining, auto: "tick" });
                updateListItem(listId, item.id, { checked: true });
              }}
            />
            <Chip
              label={t("act.remove")}
              icon="trash-can-outline"
              onPress={async () => {
                const ok = await confirm(t("ls.rowRemoved"), t("ls.rowRemovedHistory"), t("act.remove"), true);
                if (ok) removeListItem(listId, item.id);
              }}
            />
          </View>
          {item.payments.length > 0 && (
            <View style={{ marginTop: 6 }}>
              <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700", marginBottom: 2 }}>
                {t("lg.paymentHistory")}
              </Text>
              {item.payments.map((p) => (
                <Text key={p.id} style={{ fontSize: F.tiny, color: C.sub }}>
                  · {fd(p.date)} {money(p.amount)}
                  {p.method ? ` · ${p.method}` : ""}
                  {p.note ? ` · ${p.note}` : ""}
                  {p.auto ? ` (${p.auto})` : ""}
                </Text>
              ))}
            </View>
          )}
        </View>
      )}

      {paySheet && (
        <BottomSheet title={t("lg.addPayment")} onClose={() => setPaySheet(false)}>
          <Field label={t("lg.paymentAmount")} value={payAmount} onChangeText={setPayAmount} keyboardType="decimal-pad" placeholder="0" />
          {remaining > 0.005 && (
            <Btn small label={`${t("lg.fillRemaining")}: ${money(remaining)}`} variant="soft" onPress={() => setPayAmount(String(remaining))} style={{ marginBottom: 12, alignSelf: "flex-start" }} />
          )}
          <Field label={t("lg.paymentDate")} value={payDate} onChangeText={setPayDate} placeholder="YYYY-MM-DD" />
          <Field label={t("lg.paymentNote")} value={payNote} onChangeText={setPayNote} placeholder={t("lg.optionalNote")} />
          <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 6 }}>{t("lg.paymentMethod")}</Text>
          <Segmented
            value={payMethod}
            onChange={setPayMethod}
            options={[
              { key: "cash", label: "Cash" },
              { key: "bank", label: "Bank" },
              { key: "card", label: "Card" },
              { key: "mobile", label: "Mobile" },
              { key: "other", label: t("cd.other") },
            ]}
          />
          <Btn
            label={t("act.save")}
            onPress={() => {
              const val = Number(payAmount);
              if (!val || val <= 0) {
                toast(t("lg.amountGreaterThanZero"), "error");
                return;
              }
              addItemPayment(listId, item.id, {
                amount: val,
                date: payDate || todayStr(),
                method: payMethod,
                note: payNote.trim() || undefined,
              });
              setPaySheet(false);
              setPayAmount("");
              setPayNote("");
              toast(t("lg.paymentAdded"), "ok");
            }}
            style={{ marginTop: 12 }}
          />
        </BottomSheet>
      )}
    </Card>
  );
};

/* ================= ListEdit ================= */

export const ListEditOverlay: React.FC<{ params: Extract<Overlay, { type: "listEdit" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const lists = useApp((s) => s.lists);
  const saveList = useApp((s) => s.saveList);
  const existing = lists.find((l) => l.id === params.listId);
  const ownerName = useOwnerName(params.ownerId);

  const [title, setTitle] = useState(existing?.title ?? "");
  const [note, setNote] = useState(existing?.note ?? "");
  const [kind, setKind] = useState(existing?.kind ?? params.kind ?? "calculating");
  const [repeat, setRepeat] = useState<Repeat>(existing?.repeat ?? "none");
  const [postToLedger, setPostToLedger] = useState(existing?.postToLedger ?? false);
  const [isTemplate, setIsTemplate] = useState(existing?.isTemplate ?? false);

  const save = () => {
    if (!title.trim()) {
      toast(t("ls.giveListTitle"), "error");
      return;
    }
    saveList({ id: existing?.id, ownerId: params.ownerId, title: title.trim(), note: note.trim() || undefined, kind, repeat, postToLedger, isTemplate });
    toast(t("ee.saved"), "ok");
    pop();
  };

  return (
    <Sheet title={existing ? t("ls.renameEdit") : t("ls.newList")} subtitle={ownerName} onBack={pop}>
      <Field label={t("ls.listTitle")} value={title} onChangeText={setTitle} placeholder={t("ls.listPlaceholder")} autoFocus />
      <Field label={t("ls.listNote")} value={note} onChangeText={setNote} placeholder={t("ls.whatFor")} multiline />

      <SectionHeader title={t("ls.itemDetails")} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        {(["calculating", "checklist", "todo", "simple"] as const).map((k) => (
          <Chip key={k} label={t(`ls.kind${k.charAt(0).toUpperCase()}${k.slice(1)}`)} active={kind === k} onPress={() => setKind(k)} />
        ))}
      </View>

      <SectionHeader title={t("rm.repeats")} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        {(["none", "daily", "weekly", "monthly", "yearly"] as Repeat[]).map((r) => (
          <Chip key={r} label={r === "none" ? t("rm.repeatOnce") : t(`rm.repeat${r.charAt(0).toUpperCase()}${r.slice(1)}`)} active={repeat === r} onPress={() => setRepeat(r)} />
        ))}
      </View>

      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 }}>
        <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{t("ls.countPendingHint")}</Text>
        <Switch value={postToLedger} onValueChange={setPostToLedger} trackColor={{ true: C.brand, false: C.line }} thumbColor="#fff" />
      </View>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 14 }}>
        <Text style={{ flex: 1, fontSize: F.small, color: C.sub }}>{t("ls.templates")}</Text>
        <Switch value={isTemplate} onValueChange={setIsTemplate} trackColor={{ true: C.brand, false: C.line }} thumbColor="#fff" />
      </View>

      <Btn label={t("act.save")} icon="content-save-outline" onPress={save} />
    </Sheet>
  );
};

/* ---------------- helpers ---------------- */

const TotalRow: React.FC<{ label: string; value: number; C: ReturnType<typeof useThemeColors> }> = ({ label, value, C }) => (
  <View style={{ flexDirection: "row", justifyContent: "space-between" }}>
    <Text style={{ fontSize: F.small, color: C.sub }}>{label}</Text>
    <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "700" }}>{money(value)}</Text>
  </View>
);

const rowStyles = StyleSheet.create({
  tick: { width: 34, height: 34, borderRadius: 17, borderWidth: 2, alignItems: "center", justifyContent: "center" },
});

const styles = StyleSheet.create({
  addBox: { borderRadius: 16, borderWidth: 1, padding: 12, marginTop: 6 },
});
