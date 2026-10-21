/* BankSection — account cards with masked numbers, reveal/copy/share,
   holder link to contacts. */

import React, { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { useT } from "../i18n";
import { copyText, maskAccount } from "../lib/utils";
import type { BankAccount, OwnerId } from "../lib/types";
import { shareText } from "../lib/share";
import { F, useThemeColors } from "../theme";
import { Btn, Card, EmptyState, IconBtn, Money, confirm, toast, toastUndo } from "../components/kit";
import { SwipeRow } from "../components/SwipeRow";
import { BottomSheet } from "../components/Sheet";

export const BankSection: React.FC<{ ownerId: OwnerId }> = ({ ownerId }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const open = useUI((s) => s.open);
  const banks = useApp((s) => s.banks);
  const deleteBank = useApp((s) => s.deleteBank);
  const [revealed, setRevealed] = useState<Set<string>>(new Set());
  const [shareFor, setShareFor] = useState<BankAccount | null>(null);

  const mine = useMemo(
    () => banks.filter((b) => b.ownerId === ownerId && !b.deletedAt).sort((a, b) => b.updatedAt - a.updatedAt),
    [banks, ownerId]
  );
  const totalBalance = mine.reduce((s, b) => s + (b.balance ?? 0), 0);

  if (mine.length === 0) {
    return (
      <EmptyState
        icon="bank-outline"
        title={t("bk.noAccounts")}
        hint={t("bk.noAccountsHint")}
        actionLabel={t("bk.addAccount")}
        onAction={() => open({ type: "bankEdit", ownerId })}
      />
    );
  }

  const toggleReveal = (id: string) => {
    const next = new Set(revealed);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setRevealed(next);
  };

  return (
    <View>
      <View style={{ flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 10 }}>
        <Btn small label={t("bk.addAccount")} icon="plus" onPress={() => open({ type: "bankEdit", ownerId })} />
        {totalBalance !== 0 && (
          <View style={{ marginLeft: "auto" }}>
            <Money value={totalBalance} size={16} weight="800" />
          </View>
        )}
      </View>

      {mine.map((b) => (
        <View key={b.id} style={{ marginBottom: 8 }}>
          <SwipeRow
            onTap={() => open({ type: "bankEdit", bankId: b.id, ownerId })}
            leftActions={[{ icon: "eye-outline", color: "#4a7d9b", onPress: () => toggleReveal(b.id) }]}
            rightActions={[
              {
                icon: "share-variant-outline",
                color: C.brand,
                onPress: () => setShareFor(b),
              },
              {
                icon: "trash-can-outline",
                color: C.red,
                onPress: async () => {
                  const ok = await confirm(t("bk.deleteTitle"), t("bk.deleteMsg"), t("act.delete"), true);
                  if (ok) {
                    deleteBank(b.id);
                    toastUndo();
                  }
                },
              },
            ]}
          >
            <Card>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 10 }}>
                <View style={[bankStyles.icon, { backgroundColor: C.brandSoft }]}>
                  <Icon name="bank" size={19} color={C.brand} />
                </View>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
                    {b.bankName || b.label}
                  </Text>
                  <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                    {b.holderName || t("bk.holderOptional")}
                  </Text>
                </View>
                {b.balance != null && <Money value={b.balance} size={15} />}
                <IconBtn icon={revealed.has(b.id) ? "eye-off-outline" : "eye-outline"} size={34} onPress={() => toggleReveal(b.id)} />
              </View>
              <View style={{ flexDirection: "row", alignItems: "center", gap: 6, marginTop: 8 }}>
                <Text style={{ fontSize: F.small, color: C.ink, fontWeight: "600", letterSpacing: 0.5 }}>
                  {revealed.has(b.id) ? b.accountNumber : maskAccount(b.accountNumber)}
                </Text>
                {!!b.accountNumber && (
                  <IconBtn
                    icon="content-copy"
                    size={30}
                    onPress={async () => {
                      const ok = await copyText(b.accountNumber!);
                      toast(ok ? t("bk.detailsCopied") : t("bk.copyFallback"), ok ? "ok" : "error");
                    }}
                  />
                )}
              </View>
            </Card>
          </SwipeRow>
        </View>
      ))}

      {/* share sheet */}
      {shareFor && (
        <BottomSheet title={t("bk.shareAsText")} onClose={() => setShareFor(null)}>
          <Card style={{ marginBottom: 12 }}>
            <Text style={{ fontSize: F.body, color: C.ink, lineHeight: 24 }}>
              {`${shareFor.bankName || shareFor.label}\n${shareFor.holderName ? `${t("bk.holder")}: ${shareFor.holderName}\n` : ""}${t("bk.accountNumber")}: ${shareFor.accountNumber ?? t("bk.noNumber")}${shareFor.balance != null ? `\n${t("lg.balance")}: ${shareFor.balance}` : ""}`}
            </Text>
          </Card>
          <Btn
            label={t("act.share")}
            icon="share-variant-outline"
            onPress={async () => {
              const ok = await shareText(shareFor.bankName || shareFor.label, `${shareFor.bankName || shareFor.label} · ${shareFor.accountNumber ?? ""}`);
              if (ok) toast(t("bk.shared"), "ok");
              setShareFor(null);
            }}
          />
        </BottomSheet>
      )}
    </View>
  );
};

const bankStyles = StyleSheet.create({
  icon: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center" },
});

