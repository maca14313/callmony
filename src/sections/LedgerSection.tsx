/* LedgerSection — entries, groups, line items, payments, settle-up.
   Used by the General tab AND per-contact (ContactDetail). */

import React, { useMemo, useState } from "react";
import { Alert, Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import {
  entryRemaining,
  entrySignedRemaining,
  groupTotals,
  itemsCount,
  itemsTotal,
  openListsPending,
} from "../lib/calc";
import { compactMoney, fd, todayStr, copyText } from "../lib/utils";
import type { EntryKind, LedgerEntry, OwnerId } from "../lib/types";
import { shareStatement, entriesToCsv, statementText } from "../lib/exporters";
import { shareText, openURL, waLink } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Chip, Divider, EmptyState, Field, IconBtn, Money, Pill, SectionHeader, Segmented, confirm, toast, toastUndo } from "../components/kit";
import { BottomSheet } from "../components/Sheet";
import { useOwnerName } from "../components/ContactPicker";

const KIND_ICON: Record<EntryKind, string> = {
  income: "trending-up",
  expense: "trending-down",
  credit: "hand-coin-outline",
  debit: "credit-card-outline",
};

export const LedgerSection: React.FC<{ ownerId: OwnerId }> = ({ ownerId }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const entries = useApp((s) => s.entries);
  const groups = useApp((s) => s.groups);
  const lists = useApp((s) => s.lists);
  const settleUp = useApp((s) => s.settleUp);
  const deleteGroup = useApp((s) => s.deleteGroup);
  const saveGroup = useApp((s) => s.saveGroup);
  const contacts = useApp((s) => s.deviceContacts);
  const countryCode = useApp((s) => s.settings.countryCode);
  const ownerName = useOwnerName(ownerId);
  const isGeneral = ownerId === "general";

  const mine = useMemo(
    () => entries.filter((e) => e.ownerId === ownerId && !e.deletedAt),
    [entries, ownerId]
  );
  const myGroups = useMemo(
    () => groups.filter((g) => g.ownerId === ownerId && !g.deletedAt),
    [groups, ownerId]
  );

  const credit = mine.filter((e) => e.type === "credit").reduce((s, e) => s + entrySignedRemaining(e), 0);
  const debit = mine.filter((e) => e.type === "debit").reduce((s, e) => -entrySignedRemaining(e), 0);
  const pendingLists = ownerId !== "general" ? openListsPending(lists, ownerId) : 0;

  const [groupFilter, setGroupFilter] = useState<"all" | "credit" | "debit" | "unpaid" | "overdue">("all");
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const [ungroupedCollapsed, setUngroupedCollapsed] = useState(false);
  const [groupsCollapsed, setGroupsCollapsed] = useState(false);
  const [renameGroup, setRenameGroup] = useState<string | null>(null);
  const [renameTitle, setRenameTitle] = useState("");
  const [shareSheet, setShareSheet] = useState(false);

  /* the owner's first phone — used for the WhatsApp-prefilled statement */
  const ownerPhone = ownerId.startsWith("c:")
    ? contacts.find((c) => `c:${c.recordID}` === ownerId)?.phones[0]?.number
    : undefined;

  const ungrouped = useMemo(() => {
    let list = mine.filter((e) => !e.groupId);
    if (groupFilter === "credit") list = list.filter((e) => e.type === "credit");
    if (groupFilter === "debit") list = list.filter((e) => e.type === "debit");
    if (groupFilter === "unpaid") list = list.filter((e) => entryRemaining(e) > 0.005);
    if (groupFilter === "overdue")
      list = list.filter((e) => e.dueDate && e.dueDate < todayStr() && entryRemaining(e) > 0.005);
    return [...list].sort(sortEntries);
  }, [mine, groupFilter]);

  /* running balance (oldest → newest) */
  const running = useMemo(() => {
    const map = new Map<string, number>();
    let acc = 0;
    [...mine].sort(sortEntries).forEach((e) => {
      acc = Math.round((acc + entrySignedRemaining(e)) * 100) / 100;
      map.set(e.id, acc);
    });
    return map;
  }, [mine]);

  const doSettle = async () => {
    const msg = isGeneral ? t("lg.settleConfirmGeneral") : t("lg.settleConfirmPerson", { name: ownerName });
    const ok = await confirm(t("lg.settleUp"), msg, t("lg.settleUp"));
    if (!ok) return;
    settleUp(ownerId);
    toast(t("lg.settledDone"), "ok");
  };

  const shareMenu = () => setShareSheet(true);

  const exportCsv = async () => {
    const csv = entriesToCsv(mine);
    const ok = await shareText(`${ownerName}-ledger.csv`, csv);
    if (ok) toast(t("lg.csvExported"), "ok");
  };

  return (
    <View>
      {/* summary strip */}
      <View style={{ flexDirection: "row", gap: 8, marginBottom: 10 }}>
        <StatCard label={isGeneral ? t("lg.income") : t("cd.theyOweYou")} value={credit} C={C} />
        <StatCard label={isGeneral ? t("lg.expense") : t("cd.youOweThem")} value={debit} C={C} />
        <StatCard label={t("lg.net")} value={credit - debit} C={C} strong />
      </View>
      {!isGeneral && pendingLists > 0.005 && (
        <Card style={{ marginBottom: 10, padding: 10, flexDirection: "row", alignItems: "center", gap: 8 }}>
          <Icon name="information-outline" size={18} color={C.amber} />
          <Text style={{ fontSize: F.tiny, color: C.amber, flex: 1 }}>
            {t("lg.openLists")}: {compactMoney(pendingLists)} · {t("lg.countedInBalance")}
          </Text>
        </Card>
      )}

      {/* actions */}
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 6 }}>
        {(mine.length > 0 || !isGeneral) && (
          <Chip label={t("lg.settleUp")} icon="check-all" onPress={doSettle} />
        )}
        <Chip label={`+ ${t("lg.kindIncome")}`} icon="trending-up" onPress={() => open({ type: "entryEdit", ownerId, entryType: "credit", kind: isGeneral ? "income" : "credit" })} />
        <Chip label={`+ ${t("lg.kindExpense")}`} icon="trending-down" onPress={() => open({ type: "entryEdit", ownerId, entryType: "debit", kind: isGeneral ? "expense" : "debit" })} />
        <Chip label={`+ ${t("lg.kindCredit")}`} icon="hand-coin-outline" onPress={() => open({ type: "entryEdit", ownerId, entryType: "credit", kind: "credit" })} />
        <Chip label={`+ ${t("lg.kindDebit")}`} icon="credit-card-outline" onPress={() => open({ type: "entryEdit", ownerId, entryType: "debit", kind: "debit" })} />
        <Chip label={t("lg.newGroup")} icon="folder-plus-outline" onPress={() => setRenameGroup("new")} />
        <Chip label={t("lg.shareStatement")} icon="share-variant-outline" onPress={shareMenu} />
      </View>

      {/* groups */}
      {myGroups.length > 0 && (
        <View style={{ marginBottom: 10 }}>
          <SectionHeader
            title={t("lg.groups")}
            right={
              <IconBtn
                icon={groupsCollapsed ? "chevron-down" : "chevron-up"}
                onPress={() => setGroupsCollapsed((v) => !v)}
                size={34}
              />
            }
          />
          {!groupsCollapsed &&
            myGroups.map((g) => {
              const totals = groupTotals(mine, g.id);
              const collapsed = collapsedGroups.has(g.id);
              const members = mine.filter((e) => e.groupId === g.id).sort(sortEntries);
              return (
                <Card key={g.id} style={{ marginBottom: 8 }}>
                  <Pressable
                    onPress={() => toggleCollapse(setCollapsedGroups, collapsedGroups, g.id)}
                    style={{ flexDirection: "row", alignItems: "center", gap: 10 }}
                  >
                    <Icon name={collapsed ? "folder-outline" : "folder-open-outline"} size={20} color={C.brand} />
                    <View style={{ flex: 1 }}>
                      <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                        {g.title}
                      </Text>
                      <Text style={{ fontSize: F.tiny, color: C.sub }}>
                        {t("lg.entriesCount", { n: totals.count })} · {t("lg.paid")} {compactMoney(totals.paid)}
                      </Text>
                    </View>
                    <Money value={totals.remaining} signed colorize size={15} compact />
                    <IconBtn
                      icon="dots-vertical"
                      size={32}
                      onPress={() =>
                        Alert.alert(g.title, undefined, [
                          {
                            text: t("lg.addEntry"),
                            onPress: () => open({ type: "entryEdit", ownerId, groupId: g.id }),
                          },
                          {
                            text: t("act.rename"),
                            onPress: () => {
                              setRenameGroup(g.id);
                              setRenameTitle(g.title);
                            },
                          },
                          {
                            text: t("act.delete"),
                            style: "destructive",
                            onPress: async () => {
                              const ok = await confirm(t("lg.groupActions"), t("lg.noGroupEntries"), t("act.delete"), true);
                              if (ok) {
                                deleteGroup(g.id);
                                toastUndo();
                              }
                            },
                          },
                          { text: t("act.cancel"), style: "cancel" },
                        ])
                      }
                    />
                  </Pressable>
                  {!collapsed && (
                    <View style={{ marginTop: 6 }}>
                      {members.map((e) => (
                        <EntryCard key={e.id} entry={e} running={running.get(e.id) ?? 0} />
                      ))}
                      <View style={{ flexDirection: "row", gap: 6, marginTop: 8 }}>
                        <Btn small label={t("lg.addEntry")} icon="plus" variant="soft" onPress={() => open({ type: "entryEdit", ownerId, groupId: g.id })} />
                      </View>
                    </View>
                  )}
                </Card>
              );
            })}
        </View>
      )}

      {/* ungrouped entries */}
      <SectionHeader
        title={t("lg.ungrouped")}
        right={
          <IconBtn
            icon={ungroupedCollapsed ? "chevron-down" : "chevron-up"}
            onPress={() => setUngroupedCollapsed((v) => !v)}
            size={34}
          />
        }
      />
      {!ungroupedCollapsed && (
        <>
          <View style={{ flexDirection: "row", gap: 6, marginBottom: 10 }}>
            <Chip label={t("lg.filterAll")} active={groupFilter === "all"} onPress={() => setGroupFilter("all")} />
            <Chip label={isGeneral ? t("lg.income") : t("lg.credit")} active={groupFilter === "credit"} onPress={() => setGroupFilter("credit")} />
            <Chip label={isGeneral ? t("lg.expense") : t("lg.debit")} active={groupFilter === "debit"} onPress={() => setGroupFilter("debit")} />
            <Chip label={t("lg.filterUnpaid")} active={groupFilter === "unpaid"} onPress={() => setGroupFilter("unpaid")} />
            <Chip label={t("lg.filterOverdue")} active={groupFilter === "overdue"} onPress={() => setGroupFilter("overdue")} />
          </View>
          {ungrouped.length === 0 ? (
            <EmptyState
              icon="book-outline"
              title={isGeneral ? t("lg.noEntriesGeneral") : t("lg.noEntries")}
              hint={isGeneral ? t("lg.noEntriesGeneralHint") : t("lg.noEntriesHint")}
              actionLabel={t("lg.addEntry")}
              onAction={() => open({ type: "entryEdit", ownerId })}
            />
          ) : (
            ungrouped.map((e) => <EntryCard key={e.id} entry={e} running={running.get(e.id) ?? 0} />)
          )}
        </>
      )}

      {/* new/rename group sheet */}
      {renameGroup && (
        <BottomSheet title={renameGroup === "new" ? t("lg.newGroup") : t("act.rename")} onClose={() => setRenameGroup(null)}>
          <Field
            label={t("lg.groupName")}
            value={renameGroup === "new" ? renameTitle : renameTitle || myGroups.find((g) => g.id === renameGroup)?.title || ""}
            onChangeText={setRenameTitle}
            placeholder={t("lg.groupPlaceholder")}
            autoFocus
          />
          <Btn
            label={t("act.save")}
            onPress={() => {
              const title = renameTitle.trim();
              if (!title) return;
              saveGroup({ id: renameGroup === "new" ? undefined : renameGroup, ownerId, title });
              setRenameGroup(null);
              setRenameTitle("");
              toast(renameGroup === "new" ? t("lg.groupCreated") : t("lg.groupRenamed"), "ok");
            }}
          />
        </BottomSheet>
      )}

      {/* share statement sheet — WhatsApp (prefilled to the owner's phone),
          system share, copy text, CSV export */}
      {shareSheet && (
        <BottomSheet title={t("lg.shareStatement")} onClose={() => setShareSheet(false)}>
          <View style={{ gap: 8 }}>
            {ownerPhone ? (
              <Btn
                label={t("act.whatsapp")}
                icon="whatsapp"
                onPress={() => {
                  setShareSheet(false);
                  openURL(waLink(ownerPhone, statementText(ownerName, mine), countryCode));
                }}
              />
            ) : (
              !isGeneral && (
                <Text style={{ fontSize: F.small, color: C.sub, textAlign: "center" }}>
                  {t("cd.noPhone")}
                </Text>
              )
            )}
            <Btn
              label={t("act.share")}
              icon="share-variant-outline"
              variant="soft"
              onPress={async () => {
                setShareSheet(false);
                const ok = await shareStatement(ownerName, ownerId);
                if (ok) toast(t("bk.shared"), "ok");
              }}
            />
            <Btn
              label={t("act.copy")}
              icon="content-copy"
              variant="secondary"
              onPress={async () => {
                setShareSheet(false);
                const ok = await copyText(statementText(ownerName, mine));
                toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error");
              }}
            />
            <Btn
              label={t("lg.exportCsv")}
              icon="file-delimited-outline"
              variant="secondary"
              onPress={async () => {
                setShareSheet(false);
                await exportCsv();
              }}
            />
          </View>
        </BottomSheet>
      )}
    </View>
  );
};

/* ---------------- entry card ---------------- */

export const EntryCard: React.FC<{ entry: LedgerEntry; running: number }> = ({ entry, running }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const addPayment = useApp((s) => s.addPayment);
  const removePayment = useApp((s) => s.removePayment);
  const deleteEntry = useApp((s) => s.deleteEntry);
  const moveEntity = useApp((s) => s.moveEntity);
  const [expanded, setExpanded] = useState(false);
  const [paySheet, setPaySheet] = useState(false);
  const [amount, setAmount] = useState("");
  const [payDate, setPayDate] = useState(todayStr());
  const [method, setMethod] = useState("cash");
  const [payNote, setPayNote] = useState("");

  const remaining = entryRemaining(entry);
  const paid = entry.payments.reduce((s, p) => s + p.amount, 0);
  const signed = entrySignedRemaining(entry);
  const overdue = entry.dueDate && entry.dueDate < todayStr() && remaining > 0.005;
  const dueToday = entry.dueDate === todayStr() && remaining > 0.005;
  const kind = entry.kind ?? (entry.type === "credit" ? "credit" : "debit");

  const removePaymentConfirm = async (paymentId: string) => {
    const ok = await confirm(t("act.remove"), t("lg.paymentRemoved"), t("act.remove"), true);
    if (ok) removePayment(entry.id, paymentId);
  };

  return (
    <Card style={{ marginBottom: 8 }}>
      <Pressable onPress={() => setExpanded((v) => !v)} style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
        <View style={[styles.kindIcon, { backgroundColor: entry.type === "credit" ? C.brandSoft : "rgba(214,69,69,0.1)" }]}>
          <Icon name={KIND_ICON[kind] || "swap-vertical"} size={19} color={entry.type === "credit" ? C.brand : C.red} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
            <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }}>
              {t(`lg.kind${kind.charAt(0).toUpperCase()}${kind.slice(1)}`)}
            </Text>
            {!!entry.category && <Pill label={entry.category} tone="brand" />}
            {overdue && <Pill label={t("lg.paymentDue")} tone="red" />}
            {dueToday && !overdue && <Pill label={t("rm.today")} tone="amber" />}
            {!!(entry.items?.length) && (
              <Pill label={t("lg.itemsCount", { n: itemsCount(entry.items) })} tone="neutral" icon="format-list-bulleted" />
            )}
          </View>
          <Text style={{ fontSize: F.tiny, color: C.sub }}>
            {fd(entry.date)} · {t("lg.balance")} {compactMoney(running)}
            {entry.payments.length > 0 ? ` · ${t("lg.payments")} ${entry.payments.length}` : ""}
          </Text>
        </View>
        <Money value={signed} signed colorize size={16} weight="800" compact />
        <Icon name={expanded ? "chevron-up" : "chevron-down"} size={20} color={C.faint} />
      </Pressable>

      {expanded && (
        <View style={{ marginTop: 8 }}>
          <Divider />
          {/* line items — qty × price = total (Σ totals row) */}
          {!!entry.items?.length && (
            <View style={{ marginBottom: 8 }}>
              <Text style={{ fontSize: F.small, fontWeight: "800", color: C.sub, marginBottom: 4 }}>
                {t("lg.itemsTitle")}
              </Text>
              {entry.items.map((it) => (
                <View key={it.id} style={{ flexDirection: "row", alignItems: "center", paddingVertical: 4 }}>
                  <Text style={{ flex: 1, fontSize: F.small, color: C.ink }}>
                    {it.name} {it.qty}
                    {it.unit ? ` ${it.unit}` : ""} × {compactMoney(it.price)}
                  </Text>
                  <Text style={{ fontSize: F.small, fontWeight: "700", color: C.ink }}>
                    {compactMoney(it.qty * it.price)}
                  </Text>
                </View>
              ))}
              <View style={[styles.itemsTotalRow, { borderColor: C.line }]}>
                <Text style={{ fontSize: F.small, fontWeight: "800", color: C.sub }}>
                  {t("lg.total")} · {itemsCount(entry.items)}
                </Text>
                <Text style={{ fontSize: F.body, fontWeight: "800", color: C.brand }}>
                  {compactMoney(itemsTotal(entry.items))}
                </Text>
              </View>
            </View>
          )}
          {!!entry.note && (
            <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 6 }}>{entry.note}</Text>
          )}

          {/* payments */}
          <Text style={{ fontSize: F.small, fontWeight: "800", color: C.sub, marginBottom: 4 }}>
            {t("lg.paymentHistory")}
          </Text>
          {entry.payments.length === 0 && (
            <Text style={{ fontSize: F.tiny, color: C.faint, marginBottom: 6 }}>{t("lg.noPaymentsYet")}</Text>
          )}
          {entry.payments.map((p, i) => (
            <View key={p.id} style={{ flexDirection: "row", alignItems: "center", paddingVertical: 4, gap: 8 }}>
              <Text style={{ width: 18, fontSize: F.tiny, color: C.faint }}>{i + 1}.</Text>
              <Text style={{ flex: 1, fontSize: F.small, color: C.ink }}>
                {fd(p.date)} · {compactMoney(p.amount)}
                {p.auto ? ` · ${p.auto}` : ""}
                {p.method ? ` · ${p.method}` : ""}
                {p.note ? ` · ${p.note}` : ""}
              </Text>
              <IconBtn icon="close" size={28} onPress={() => removePaymentConfirm(p.id)} danger />
            </View>
          ))}
          {remaining > 0.005 && (
            <View style={{ marginBottom: 6 }}>
              <Pill
                label={paid > 0 ? `${t("lg.paid")} ${compactMoney(paid)} / ${compactMoney(entry.amount)}` : `${t("lg.pending")} ${compactMoney(remaining)}`}
                tone={paid > 0 ? "amber" : "red"}
              />
            </View>
          )}
          {remaining < -0.005 && (
            <View style={{ marginBottom: 6 }}>
              <Pill label={`${t("lg.overpaidBy")} ${compactMoney(-remaining)}`} tone="green" />
            </View>
          )}
          {Math.abs(remaining) <= 0.005 && (
            <View style={{ marginBottom: 6 }}>
              <Pill label="✓" tone="green" />
            </View>
          )}

          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
            <Btn small label={t("lg.addPayment")} icon="plus" variant="soft" onPress={() => setPaySheet(true)} />
            <Btn small label={t("act.edit")} icon="pencil-outline" variant="secondary" onPress={() => open({ type: "entryEdit", entryId: entry.id, ownerId: entry.ownerId })} />
            <Btn
              small
              label={t("act.move")}
              icon="account-arrow-right-outline"
              variant="secondary"
              onPress={async () => {
                const okC = await confirm(t("lg.entryMoved"), t("lg.movedToGeneral"), t("act.move"));
                if (okC) {
                  moveEntity("entry", entry.id, "general");
                  toast(t("lg.entryMoved"), "ok");
                }
              }}
            />
            <Btn
              small
              label={t("act.delete")}
              icon="trash-can-outline"
              variant="danger"
              onPress={async () => {
                const okD = await confirm(t("lg.deleteEntryTitle"), t("lg.deleteEntryMsg"), t("act.delete"), true);
                if (okD) {
                  deleteEntry(entry.id);
                  toastUndo();
                }
              }}
            />
          </View>
        </View>
      )}

      {/* add payment sheet */}
      {paySheet && (
        <BottomSheet title={t("lg.addPayment")} onClose={() => setPaySheet(false)}>
          <Field label={t("lg.paymentAmount")} value={amount} onChangeText={setAmount} keyboardType="decimal-pad" placeholder="0" />
          {remaining > 0.005 && (
            <Btn small label={`${t("lg.fillRemaining")}: ${compactMoney(remaining)}`} variant="soft" onPress={() => setAmount(String(remaining))} style={{ marginBottom: 12, alignSelf: "flex-start" }} />
          )}
          <Field label={t("lg.paymentDate")} value={payDate} onChangeText={setPayDate} placeholder="YYYY-MM-DD" />
          <Field label={t("lg.paymentNote")} value={payNote} onChangeText={setPayNote} placeholder={t("lg.optionalNote")} />
          <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 6 }}>{t("lg.paymentMethod")}</Text>
          <Segmented
            value={method}
            onChange={setMethod}
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
              const val = Number(amount);
              if (!val || val <= 0) {
                toast(t("lg.amountGreaterThanZero"), "error");
                return;
              }
              addPayment(entry.id, { amount: val, date: payDate || todayStr(), method, note: payNote || undefined });
              setPaySheet(false);
              setAmount("");
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

/* ---------------- helpers ---------------- */

const sortEntries = (a: LedgerEntry, b: LedgerEntry) =>
  a.date < b.date ? 1 : a.date > b.date ? -1 : b.createdAt - a.createdAt;

const toggleCollapse = (
  setter: React.Dispatch<React.SetStateAction<Set<string>>>,
  set: Set<string>,
  id: string
) => {
  const next = new Set(set);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  setter(next);
};

const StatCard: React.FC<{ label: string; value: number; C: ReturnType<typeof useThemeColors>; strong?: boolean }> = ({ label, value, C, strong }) => (
  <View style={[statStyles.card, { backgroundColor: strong ? C.brandSoft : C.card, borderColor: strong ? C.brand : C.line }]}>
    <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "600" }}>{label}</Text>
    <Money value={value} colorize size={16} weight="800" compact />
  </View>
);

const groupMenuUnused = null;
void groupMenuUnused;

const statStyles = StyleSheet.create({
  card: { flex: 1, borderRadius: 14, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 9, gap: 2, minWidth: 96 },
});

const styles = StyleSheet.create({
  kindIcon: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  itemsTotalRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    borderTopWidth: 1,
    borderStyle: "dashed",
    marginTop: 4,
    paddingTop: 6,
  },
});
