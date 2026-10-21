/* EntryEdit — new/edit a ledger entry.
   Line items: name + qty (+ unit) × unit price → line total; the amount
   is the live Σ of the lines; the totals row shows Σ qty and Σ amount
   (e.g. ball 3 pic × 100 = 300 · orange 4 kg × 200 = 800 · t-shirt 1 bag
   × 1000 → totals: 8 and 2100). */

import React, { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { itemsTotal, itemsCount } from "../lib/calc";
import { money, todayStr, uid } from "../lib/utils";
import { ENTRY_CATEGORIES, type EntryItem, type EntryKind, type OwnerId } from "../lib/types";
import { F, useThemeColors } from "../theme";
import { Btn, Chip, Divider, EmptyState, Field, IconBtn, SectionHeader, confirm, toast } from "../components/kit";
import { Sheet } from "../components/Sheet";
import { ContactPicker, useOwnerName } from "../components/ContactPicker";

export const EntryEditOverlay: React.FC<{ params: Extract<Overlay, { type: "entryEdit" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const openCalculator = useUI((s) => s.openCalculator);
  const entries = useApp((s) => s.entries);
  const groups = useApp((s) => s.groups);
  const saveEntry = useApp((s) => s.saveEntry);
  const deleteEntry = useApp((s) => s.deleteEntry);
  const saveReminder = useApp((s) => s.saveReminder);
  const addCustomCategory = useApp((s) => s.addCustomCategory);
  const removeCustomCategory = useApp((s) => s.removeCustomCategory);
  const customCategories = useApp((s) => s.settings.customCategories ?? []);
  const ownerName = useOwnerName(params.ownerId);

  const existing = useMemo(
    () => entries.find((e) => e.id === params.entryId),
    [entries, params.entryId]
  );

  const [ownerId, setOwnerId] = useState<OwnerId>(existing?.ownerId ?? params.ownerId);
  const [type, setType] = useState<"credit" | "debit">(existing?.type ?? params.entryType ?? "credit");
  const [kind, setKind] = useState<EntryKind>(existing?.kind ?? params.kind ?? (params.entryType === "debit" ? "debit" : "credit"));
  const [amount, setAmount] = useState(String(existing?.amount ?? ""));
  const [items, setItems] = useState<EntryItem[]>(existing?.items ?? []);
  const [date, setDate] = useState(existing?.date ?? todayStr());
  const [dueDate, setDueDate] = useState(existing?.dueDate ?? "");
  const [category, setCategory] = useState(existing?.category ?? "");
  const [method, setMethod] = useState(existing?.method ?? "cash");
  const [note, setNote] = useState(existing?.note ?? "");
  const [groupId, setGroupId] = useState<string | undefined>(existing?.groupId ?? params.groupId);
  const [hasDue, setHasDue] = useState(!!existing?.dueDate);
  const [catManage, setCatManage] = useState(false);
  const [newCat, setNewCat] = useState("");
  const [pickOwner, setPickOwner] = useState(false);

  // line-item editor state
  const [liName, setLiName] = useState("");
  const [liQty, setLiQty] = useState("1");
  const [liUnit, setLiUnit] = useState("");
  const [liPrice, setLiPrice] = useState("");

  const ownerGroups = useMemo(
    () => groups.filter((g) => g.ownerId === ownerId && !g.deletedAt),
    [groups, ownerId]
  );

  const liveAmount = items.length ? itemsTotal(items) : Number(amount) || 0;
  const editing = !!existing;

  const addLine = () => {
    const name = liName.trim();
    const qty = Number(liQty) || 0;
    const price = Number(liPrice) || 0;
    if (!name || qty <= 0 || price < 0 || (!liPrice && price === 0)) {
      toast(t("lg.itemIncomplete"), "error");
      return;
    }
    setItems((arr) => [
      ...arr,
      { id: uid(), name, qty, unit: liUnit.trim() || undefined, price },
    ]);
    setLiName("");
    setLiQty("1");
    setLiUnit("");
    setLiPrice("");
    toast(t("lg.itemAdded"), "ok");
  };

  const save = () => {
    const finalAmount = items.length ? itemsTotal(items) : Number(amount) || 0;
    if (finalAmount <= 0) {
      toast(t("lg.amountGreaterThanZero"), "error");
      return;
    }
    saveEntry({
      id: existing?.id,
      ownerId,
      type,
      kind,
      amount: finalAmount,
      items: items.length ? items : undefined,
      date: date || todayStr(),
      dueDate: hasDue && dueDate ? dueDate : undefined,
      category: category || undefined,
      method,
      note: note.trim() || undefined,
      groupId,
    });
    // auto payment-reminder at 09:00 on the due date for new entries
    if (!editing && hasDue && dueDate) {
      const due = new Date(`${dueDate}T09:00:00`);
      if (!isNaN(due.getTime())) {
        saveReminder({
          ownerId,
          title: t("lg.paymentDue"),
          kind: "payment",
          dueAt: due.getTime(),
          notes: `${t("lg.ledgerEntry")}: ${money(finalAmount)}`,
        });
      }
    }
    toast(editing ? t("ee.saved") : t("lg.addEntry"), "ok");
    pop();
  };

  const remove = async () => {
    const ok = await confirm(t("lg.deleteEntryTitle"), t("lg.deleteEntryMsg"), t("act.delete"), true);
    if (!ok) return;
    deleteEntry(existing!.id);
    pop();
  };

  const kindOptions: { key: EntryKind; label: string; type: "credit" | "debit" }[] = [
    { key: "income", label: t("lg.kindIncome"), type: "credit" },
    { key: "expense", label: t("lg.kindExpense"), type: "debit" },
    { key: "credit", label: t("lg.kindCredit"), type: "credit" },
    { key: "debit", label: t("lg.kindDebit"), type: "debit" },
  ];

  return (
    <Sheet
      title={editing ? t("lg.editEntry") : t("lg.newEntry")}
      subtitle={ownerName}
      onBack={pop}
      footer={
        <View style={{ flexDirection: "row", gap: 10 }}>
          {editing && (
            <Btn label={t("act.delete")} icon="trash-can-outline" variant="danger" onPress={remove} style={{ flex: 0.6 }} />
          )}
          <Btn label={t("act.save")} icon="content-save-outline" onPress={save} style={{ flex: 1 }} />
        </View>
      }
    >
      {/* kind selector */}
      <View style={{ flexDirection: "row", gap: 6, flexWrap: "wrap", marginBottom: 14 }}>
        {kindOptions.map((k) => (
          <Chip
            key={k.key}
            label={k.label}
            icon={k.type === "credit" ? "trending-up" : "trending-down"}
            active={kind === k.key}
            onPress={() => {
              setKind(k.key);
              setType(k.type);
            }}
          />
        ))}
      </View>

      {/* amount */}
      <View style={{ marginBottom: 6 }}>
        <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "600", marginBottom: 6 }}>
          {t("lg.entryAmount")}
        </Text>
        <View style={[styles.amountRow, { borderColor: C.brand, backgroundColor: C.brandSoft }]}>
          <Text style={{ fontSize: 26, fontWeight: "800", color: C.brand, flex: 1 }} adjustsFontSizeToFit minimumFontScale={0.4}>
            {money(liveAmount)}
          </Text>
          {!items.length && (
            <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
              <TextInput
                value={amount}
                onChangeText={setAmount}
                keyboardType="decimal-pad"
                placeholder="0"
                placeholderTextColor={C.faint}
                style={{ fontSize: 18, fontWeight: "700", color: C.brand, minWidth: 90, textAlign: "right" }}
              />
              <IconBtn icon="calculator" size={36} onPress={() => openCalculator((v) => setAmount(String(v)))} />
            </View>
          )}
          {!!items.length && (
            <View style={{ alignItems: "flex-end", gap: 2 }}>
              <Text style={{ fontSize: F.tiny, color: C.sub }}>{t("lg.autoFromItems")}</Text>
              <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "700" }}>
                {t("lg.itemsCount", { n: itemsCount(items) })}
              </Text>
            </View>
          )}
        </View>
      </View>

      {/* line items */}
      <SectionHeader title={t("lg.itemsTitle")} subtitle={t("lg.itemsHint")} />
      {items.length === 0 && (
        <Text style={{ fontSize: F.small, color: C.faint, marginBottom: 8 }}>{t("lg.noItemsYet")}</Text>
      )}
      {items.map((it) => (
        <View key={it.id} style={[styles.lineItem, { borderColor: C.line, backgroundColor: C.card }]}>
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }}>
              {it.name} · {it.qty}
              {it.unit ? ` ${it.unit}` : ""}
            </Text>
            <Text style={{ fontSize: F.small, color: C.sub }}>
              {it.qty} {it.unit ? `${it.unit} ` : ""}× {money(it.price)} = <Text style={{ color: C.ink, fontWeight: "700" }}>{money(it.qty * it.price)}</Text>
            </Text>
          </View>
          <IconBtn
            icon="close-circle-outline"
            size={34}
            danger
            onPress={() => setItems((arr) => arr.filter((x) => x.id !== it.id))}
          />
        </View>
      ))}
      {items.length > 0 && (
        <View style={[styles.itemsTotalRow, { borderColor: C.line }]}>
          <Text style={{ fontSize: F.small, fontWeight: "800", color: C.sub }}>
            {t("lg.total")} · {itemsCount(items)}
          </Text>
          <Text style={{ fontSize: 20, fontWeight: "800", color: C.brand }}>{money(itemsTotal(items))}</Text>
        </View>
      )}

      {/* add-item form */}
      <View style={[styles.addItemBox, { borderColor: C.line, backgroundColor: C.cardAlt }]}>
        <View style={{ flexDirection: "row", gap: 8 }}>
          <View style={{ flex: 1.5 }}>
            <Field label={t("lg.itemName")} value={liName} onChangeText={setLiName} placeholder={t("lg.itemNamePh")} />
          </View>
          <View style={{ flex: 0.7 }}>
            <Field label={t("lg.qty")} value={liQty} onChangeText={setLiQty} keyboardType="decimal-pad" placeholder={t("lg.qtyPh")} />
          </View>
        </View>
        <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-end" }}>
          <View style={{ flex: 0.9 }}>
            <Field label={t("lg.unit")} value={liUnit} onChangeText={setLiUnit} placeholder={t("lg.unitPh")} />
          </View>
          <View style={{ flex: 1.3 }}>
            <Field
              label={t("lg.unitPrice")}
              value={liPrice}
              onChangeText={setLiPrice}
              keyboardType="decimal-pad"
              placeholder="0"
              right={
                <IconBtn icon="calculator" size={34} onPress={() => openCalculator((v) => setLiPrice(String(v)))} />
              }
            />
          </View>
          <Btn label={t("act.add")} icon="plus" onPress={addLine} style={{ marginBottom: 12, minHeight: 48 }} />
        </View>
        {liName.length === 0 && (
          <View style={{ flexDirection: "row", gap: 6, flexWrap: "wrap" }}>
            {["pic", "kg", "pcs", "bag", "box", "l"].map((u) => (
              <Chip key={u} label={u} onPress={() => setLiUnit(u)} active={liUnit === u} />
            ))}
          </View>
        )}
      </View>

      <Divider />

      {/* date + method */}
      <View style={{ flexDirection: "row", gap: 8 }}>
        <View style={{ flex: 1 }}>
          <Field label={t("lg.entryDate")} value={date} onChangeText={setDate} placeholder="YYYY-MM-DD" />
        </View>
        <View style={{ flex: 1 }}>
          <Field label={t("lg.method")} value={method} onChangeText={setMethod} placeholder="cash" />
        </View>
      </View>
      <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 10 }}>
        {t("lg.paymentMethod")}: cash · bank · card · mobile · other
      </Text>

      {/* category */}
      <SectionHeader title={t("cat.category")} right={<Chip label={t("cat.custom")} icon="tag-outline" onPress={() => setCatManage(true)} />} />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
        {ENTRY_CATEGORIES.map((cat) => (
          <Chip key={cat} label={cat} active={category === cat} onPress={() => setCategory(category === cat ? "" : cat)} />
        ))}
        {customCategories.map((cat) => (
          <View key={cat} style={{ flexDirection: "row", alignItems: "center" }}>
            <Chip label={cat} active={category === cat} onPress={() => setCategory(category === cat ? "" : cat)} />
          </View>
        ))}
      </View>

      {/* due date */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 12 }}>
        <Chip
          label={t("lg.dueDate")}
          icon="calendar-clock-outline"
          active={hasDue}
          onPress={() => setHasDue((v) => !v)}
        />
        {hasDue && (
          <View style={{ flex: 1 }}>
            <Field value={dueDate} onChangeText={setDueDate} placeholder="YYYY-MM-DD" />
          </View>
        )}
      </View>

      {/* note */}
      <Field label={t("lg.entryNote")} value={note} onChangeText={setNote} placeholder={t("lg.optionalNote")} multiline />

      {/* owner */}
      <SectionHeader title={t("ee.owner")} />
      <Pressable onPress={() => setPickOwner(true)} style={[styles.ownerRow, { borderColor: C.line, backgroundColor: C.card }]}>
        <Icon name="account-circle-outline" size={22} color={C.brand} />
        <Text style={{ flex: 1, fontSize: F.body, color: C.ink, fontWeight: "600" }}>{ownerName}</Text>
        <Icon name="swap-horizontal" size={20} color={C.sub} />
      </Pressable>

      {/* groups */}
      {ownerGroups.length > 0 && (
        <>
          <SectionHeader title={t("lg.group")} />
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
            <Chip label={t("ee.noGroup")} active={!groupId} onPress={() => setGroupId(undefined)} />
            {ownerGroups.map((g) => (
              <Chip key={g.id} label={g.title} active={groupId === g.id} onPress={() => setGroupId(g.id)} />
            ))}
          </View>
        </>
      )}

      {/* category manage sheet */}
      {catManage && (
        <Sheet title={t("cat.category")} onBack={() => setCatManage(false)}>
          <Field label={t("cat.categoryName")} value={newCat} onChangeText={setNewCat} placeholder={t("cat.categoryName")} />
          <Btn
            label={t("cat.addCustom")}
            icon="plus"
            small
            onPress={() => {
              addCustomCategory(newCat);
              setNewCat("");
            }}
            style={{ alignSelf: "flex-start", marginBottom: 14 }}
          />
          <Text style={{ fontSize: F.small, color: C.faint, marginBottom: 8 }}>{t("cat.customHint")}</Text>
          {customCategories.map((cat) => (
            <View key={cat} style={[styles.lineItem, { borderColor: C.line, backgroundColor: C.card }]}>
              <Text style={{ flex: 1, fontSize: F.body, color: C.ink }}>{cat}</Text>
              <IconBtn
                icon="trash-can-outline"
                size={34}
                danger
                onPress={() => {
                  removeCustomCategory(cat);
                  if (category === cat) setCategory("");
                }}
              />
            </View>
          ))}
          {customCategories.length === 0 && <EmptyState icon="tag-outline" title={t("cat.custom")} hint={t("cat.customHint")} />}
        </Sheet>
      )}

      {/* owner picker */}
      {pickOwner && (
        <ContactPicker
          title={t("ee.owner")}
          onClose={() => setPickOwner(false)}
          onPick={(id) => {
            setOwnerId(id as OwnerId);
            setGroupId(undefined);
            setPickOwner(false);
          }}
        />
      )}
    </Sheet>
  );
};

const styles = StyleSheet.create({
  amountRow: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 18,
    borderWidth: 1.5,
    paddingHorizontal: 16,
    paddingVertical: 14,
    minHeight: 74,
  },
  lineItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 13,
    paddingVertical: 10,
    marginBottom: 6,
  },
  itemsTotalRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    borderTopWidth: 1.5,
    borderStyle: "dashed",
    marginTop: 4,
    marginBottom: 10,
    paddingTop: 8,
  },
  addItemBox: { borderRadius: 16, borderWidth: 1, padding: 12, marginBottom: 8, gap: 2 },
  ownerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 10,
  },
});
