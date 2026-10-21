/* Settings + LockScreen + PIN flows.
   Appearance · Language · Hide balances · Notifications · Region ·
   Security (PIN + autolock) · Backup & restore · Import & export ·
   About · Danger zone. */

import React, { useState } from "react";
import { Alert, Animated, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import DocumentPicker from "react-native-document-picker";
import RNFS from "react-native-fs";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI } from "../lib/ui-store";
import { LANGUAGES, useT } from "../i18n";
import { hashPin, relTime } from "../lib/utils";
import { COUNTRY_CODES, CURRENCIES } from "../lib/types";
import { shareText } from "../lib/share";
import { exportContactsCsv, exportContactsVcard, exportTransactionsCsv } from "../lib/exporters";
import { requestNotificationPermission } from "../native/notify";
import { fetchAllContacts } from "../native/deviceContacts";
import { F, useThemeColors } from "../theme";
import { Btn, Card, Divider, SectionHeader, Segmented, confirm, toast } from "../components/kit";
import { Sheet, BottomSheet } from "../components/Sheet";
import { LanguageSheet } from "../components/LanguageSheet";
import { ContactPicker } from "../components/ContactPicker";

export const SettingsOverlay: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const open = useUI((s) => s.open);
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);
  const wipeAll = useApp((s) => s.wipeAll);
  const restoreBackup = useApp((s) => s.restoreBackup);
  const setDeviceContacts = useApp((s) => s.setDeviceContacts);
  const [langSheet, setLangSheet] = useState(false);
  const [pinSheet, setPinSheet] = useState<"set" | "change" | "remove" | null>(null);

  const language = LANGUAGES.find((l) => l.code === settings.language);

  const exportBackup = async () => {
    const s = useApp.getState();
    const backup = {
      app: "callmony",
      version: 1,
      exportedAt: Date.now(),
      data: {
        contactX: s.contactX,
        entries: s.entries,
        groups: s.groups,
        lists: s.lists,
        notes: s.notes,
        reminders: s.reminders,
        banks: s.banks,
        recents: s.recents,
        messages: s.messages,
        settings: { language: s.settings.language, currency: s.settings.currency, countryCode: s.settings.countryCode },
      },
    };
    const date = new Date();
    const stamp = `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}`;
    const ok = await shareText(`callmony-backup-${stamp}.json`, JSON.stringify(backup, null, 1));
    if (ok) {
      updateSettings({ lastBackupAt: Date.now() });
      toast(t("bkp.backupExported"), "ok");
    }
  };

  const restoreFrom = async () => {
    try {
      const picked = await DocumentPicker.pickSingle({ type: [DocumentPicker.types.allFiles] });
      if (!picked.uri) return;
      const content = await RNFS.readFile(decodeURIComponent(picked.uri.replace("file://", "")), "utf8");
      const parsed = JSON.parse(content) as { app?: string; data?: Record<string, unknown> };
      if (parsed?.app !== "callmony" || !parsed?.data) {
        toast(t("bkp.notValidBackup"), "error");
        return;
      }
      Alert.alert(t("bkp.restoreBackup"), t("bkp.safetyNote"), [
        {
          text: t("misc.merge"),
          onPress: () => {
            restoreBackup(parsed.data as Parameters<typeof restoreBackup>[0], "merge");
            toast(t("bkp.restored"), "ok");
            pop();
          },
        },
        {
          text: t("misc.replace"),
          style: "destructive",
          onPress: async () => {
            const ok = await confirm(t("bkp.replaceTitle"), t("bkp.replaceMsg"), t("bkp.eraseAndRestore"), true);
            if (!ok) return;
            restoreBackup(parsed.data as Parameters<typeof restoreBackup>[0], "replace");
            toast(t("bkp.restored"), "ok");
            pop();
          },
        },
        { text: t("act.cancel"), style: "cancel" },
      ]);
    } catch {
      /* picker cancelled */
    }
  };

  const askNotifs = async () => {
    const ok = await requestNotificationPermission();
    updateSettings({ notifAsked: true });
    toast(ok ? t("rm.notifGranted") : t("rm.notifDeclined"), ok ? "ok" : "error");
  };

  const wipe = async () => {
    const ok1 = await confirm(t("st.deleteAllTitle"), t("st.deleteAllMsg"), t("st.deleteEverything"), true);
    if (!ok1) return;
    const ok2 = await confirm(t("st.reallyDeleteTitle"), t("st.reallyDeleteMsg"), t("st.wipeMyData"), true);
    if (!ok2) return;
    await wipeAll();
    const contacts = await fetchAllContacts().catch(() => []);
    setDeviceContacts(contacts);
    toast(t("st.allDataDeleted"), "ok");
  };

  return (
    <Sheet title={t("hdr.settings")} onBack={pop}>
      {/* appearance */}
      <SectionHeader title={t("st.appearance")} />
      <Card style={{ marginBottom: 12 }}>
        <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 8 }}>{t("st.theme")}</Text>
        <Segmented
          value={settings.theme}
          onChange={(k) => updateSettings({ theme: k as "light" | "dark" | "auto" })}
          options={[
            { key: "light", label: t("st.light") },
            { key: "dark", label: t("st.dark") },
            { key: "auto", label: t("st.auto") },
          ]}
        />
        <Divider />
        <Pressable onPress={() => setLangSheet(true)} style={setStyles.row}>
          <Icon name="translate" size={20} color={C.brand} />
          <Text style={{ flex: 1, fontSize: F.body, color: C.ink }}>{t("st.language")}</Text>
          <Text style={{ fontSize: F.small, color: C.sub }}>{language?.native ?? "English"}</Text>
          <Icon name="chevron-right" size={20} color={C.faint} />
        </Pressable>
        <Divider />
        <View style={setStyles.row}>
          <Icon name="eye-off-outline" size={20} color={C.brand} />
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.body, color: C.ink }}>{t("st.hideBalances")}</Text>
            <Text style={{ fontSize: F.tiny, color: C.sub }}>{t("st.hideBalancesHint")}</Text>
          </View>
          <Segmented
            value={settings.hideBalances ? "on" : "off"}
            onChange={(k) => updateSettings({ hideBalances: k === "on" })}
            options={[
              { key: "off", label: t("misc.off") },
              { key: "on", label: t("misc.on") },
            ]}
          />
        </View>
      </Card>

      {/* notifications */}
      <SectionHeader title={t("st.notifications")} />
      <Card style={{ marginBottom: 12 }}>
        <View style={setStyles.row}>
          <Icon name="bell-outline" size={20} color={C.brand} />
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.body, color: C.ink }}>{t("st.allowNotifications")}</Text>
            <Text style={{ fontSize: F.tiny, color: C.sub }}>{t("st.notificationsHint")}</Text>
          </View>
          <Btn small label={t("st.allow")} variant="soft" onPress={askNotifs} />
        </View>
      </Card>

      {/* region */}
      <SectionHeader title={t("st.region")} />
      <Card style={{ marginBottom: 12 }}>
        <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 8 }}>{t("st.currency")}</Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6, marginBottom: 12 }}>
          {CURRENCIES.map((c) => (
            <Pressable
              key={c.code}
              onPress={() => updateSettings({ currency: c.code })}
              style={[setStyles.pill, { borderColor: settings.currency === c.code ? C.brand : C.line, backgroundColor: settings.currency === c.code ? C.brandSoft : "transparent" }]}
            >
              <Text style={{ fontSize: F.small, color: settings.currency === c.code ? C.brand : C.sub, fontWeight: "700" }}>
                {c.symbol} {c.code}
              </Text>
            </Pressable>
          ))}
        </View>
        <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 8 }}>{t("st.countryCode")}</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6 }}>
          {COUNTRY_CODES.map((c) => (
            <Pressable
              key={c.code}
              onPress={() => updateSettings({ countryCode: c.code })}
              style={[setStyles.pill, { borderColor: settings.countryCode === c.code ? C.brand : C.line, backgroundColor: settings.countryCode === c.code ? C.brandSoft : "transparent" }]}
            >
              <Text style={{ fontSize: F.small, color: settings.countryCode === c.code ? C.brand : C.sub, fontWeight: "700" }}>
                +{c.dial}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
        <Text style={{ fontSize: F.tiny, color: C.faint, marginTop: 6 }}>{t("st.countryCodeHint")}</Text>
      </Card>

      {/* security */}
      <SectionHeader title={t("st.security")} />
      <Card style={{ marginBottom: 12 }}>
        <View style={setStyles.row}>
          <Icon name="lock-outline" size={20} color={C.brand} />
          <View style={{ flex: 1 }}>
            <Text style={{ fontSize: F.body, color: C.ink }}>{t("st.pin")}</Text>
            <Text style={{ fontSize: F.tiny, color: C.sub }}>{t("st.pinHint")}</Text>
          </View>
        </View>
        <View style={{ flexDirection: "row", gap: 8, marginTop: 10 }}>
          {settings.pinHash ? (
            <>
              <Btn small label={t("st.changePin")} variant="soft" onPress={() => setPinSheet("change")} />
              <Btn small label={t("st.removePin")} variant="secondary" onPress={() => setPinSheet("remove")} />
            </>
          ) : (
            <Btn small label={t("st.setPin")} icon="lock-outline" onPress={() => setPinSheet("set")} />
          )}
        </View>
        {settings.pinHash && (
          <>
            <Divider />
            <Text style={{ fontSize: F.small, color: C.sub, marginBottom: 6 }}>{t("st.autolock")}</Text>
            <Segmented
              value={String(settings.autolockMin)}
              onChange={(k) => updateSettings({ autolockMin: Number(k) })}
              options={[
                { key: "0", label: t("st.immediately") },
                { key: "1", label: t("st.after1") },
                { key: "5", label: t("st.after5") },
              ]}
            />
          </>
        )}
      </Card>

      {/* backup */}
      <SectionHeader title={t("st.backup")} />
      <Card style={{ marginBottom: 12 }}>
        <Text style={{ fontSize: F.tiny, color: C.sub, marginBottom: 8 }}>{t("bkp.includesNote")}</Text>
        <View style={{ flexDirection: "row", gap: 8, marginBottom: 8 }}>
          <Btn small label={t("bkp.exportBackup")} icon="export" variant="soft" onPress={exportBackup} style={{ flex: 1 }} />
          <Btn small label={t("st.restore")} icon="import" variant="secondary" onPress={restoreFrom} style={{ flex: 1 }} />
        </View>
        {settings.lastBackupAt ? (
          <Text style={{ fontSize: F.tiny, color: C.faint }}>
            {t("bkp.lastBackup")}: {relTime(settings.lastBackupAt)}
            {Date.now() - settings.lastBackupAt > 30 * 86400000 ? ` · ${t("bkp.stale")}` : ""}
          </Text>
        ) : null}
      </Card>

      {/* import & export */}
      <SectionHeader title={t("ie.importExport")} />
      <Card style={{ marginBottom: 12 }}>
        <Text style={{ fontSize: F.tiny, color: C.sub, marginBottom: 8 }}>
          {t("ie.fromThisPhone")} — {t("ie.pickedWith")}
        </Text>
        <View style={{ flexDirection: "row", gap: 8, flexWrap: "wrap" }}>
          <Btn small label={t("ie.exportVcf")} icon="card-account-details-outline" variant="soft" onPress={() => exportContactsVcard()} />
          <Btn small label={t("ie.exportCsv")} icon="file-delimited-outline" variant="soft" onPress={() => exportContactsCsv()} />
          <Btn small label={t("ie.exportTx")} icon="swap-vertical" variant="soft" onPress={() => exportTransactionsCsv()} />
        </View>
      </Card>

      {/* about */}
      <Card style={{ marginBottom: 12 }}>
        <Pressable onPress={() => open({ type: "about" })} style={setStyles.row}>
          <Icon name="information-outline" size={20} color={C.brand} />
          <Text style={{ flex: 1, fontSize: F.body, color: C.ink }}>{t("st.aboutApp")}</Text>
          <Icon name="chevron-right" size={20} color={C.faint} />
        </Pressable>
      </Card>

      {/* danger zone */}
      <Card style={{ marginBottom: 20, borderColor: C.red }}>
        <Text style={{ fontSize: F.small, fontWeight: "800", color: C.red, marginBottom: 6 }}>
          {t("st.deleteAllData")}
        </Text>
        <Text style={{ fontSize: F.tiny, color: C.sub, marginBottom: 8 }}>{t("st.deleteAllHint")}</Text>
        <Btn small label={t("st.wipeMyData")} icon="alert-outline" variant="danger" onPress={wipe} />
      </Card>

      {langSheet && <LanguageSheet onClose={() => setLangSheet(false)} />}
      {pinSheet && <PinFlow mode={pinSheet} onClose={() => setPinSheet(null)} />}
    </Sheet>
  );
};

/* ---------------- PIN flow sheet ---------------- */

export const PinFlow: React.FC<{ mode: "set" | "change" | "remove"; onClose: () => void }> = ({ mode, onClose }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);
  const [stage, setStage] = useState<"current" | "new" | "confirm">(mode === "set" ? "new" : "current");
  const [value, setValue] = useState("");
  const [first, setFirst] = useState("");
  const [shake] = useState(new Animated.Value(0));

  const doShake = () => {
    Animated.sequence([
      Animated.timing(shake, { toValue: 10, duration: 60, useNativeDriver: true }),
      Animated.timing(shake, { toValue: -10, duration: 60, useNativeDriver: true }),
      Animated.timing(shake, { toValue: 0, duration: 60, useNativeDriver: true }),
    ]).start();
  };

  const finish = (pin: string) => {
    updateSettings({ pinHash: hashPin(pin) });
    toast(t("st.enablePinConfirm"), "ok");
    onClose();
  };

  const submit = (pin: string) => {
    if (stage === "current") {
      const ok = hashPin(pin, settings.pinHash?.salt).hash === settings.pinHash?.hash;
      if (!ok) {
        doShake();
        setValue("");
        toast(t("st.wrongPin"), "error");
        return;
      }
      if (mode === "remove") {
        updateSettings({ pinHash: undefined });
        toast(t("st.pinRemoved"), "ok");
        onClose();
        return;
      }
      setStage("new");
      setValue("");
      return;
    }
    if (stage === "new") {
      setFirst(pin);
      setStage("confirm");
      setValue("");
      return;
    }
    // confirm
    if (pin !== first) {
      doShake();
      setValue("");
      toast(t("st.pinsDontMatch"), "error");
      setStage("new");
      return;
    }
    finish(pin);
  };

  const title =
    stage === "current" ? t("st.enterCurrentPin") : stage === "new" ? t("st.choosePin") : t("st.reenterPin");

  return (
    <BottomSheet title={mode === "remove" ? t("st.removePin") : title} onClose={onClose}>
      <Animated.View style={{ transform: [{ translateX: shake }] }}>
        <PinPad value={value} onChange={(v) => { setValue(v); if (v.length === 4) setTimeout(() => submit(v), 120); }} />
      </Animated.View>
      <Text style={{ fontSize: F.tiny, color: C.sub, textAlign: "center", marginTop: 10, marginBottom: 4 }}>
        {mode === "set" ? t("st.changePinConfirm") : t("st.pinHint")}
      </Text>
    </BottomSheet>
  );
};

/* ---------------- LockScreen ---------------- */

export const LockScreen: React.FC = () => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const setLocked = useUI((s) => s.setLocked);
  const settings = useApp((s) => s.settings);
  const updateSettings = useApp((s) => s.updateSettings);
  const [value, setValue] = useState("");
  const [shake] = useState(new Animated.Value(0));

  const doShake = () => {
    Animated.sequence([
      Animated.timing(shake, { toValue: 12, duration: 60, useNativeDriver: true }),
      Animated.timing(shake, { toValue: -12, duration: 60, useNativeDriver: true }),
      Animated.timing(shake, { toValue: 0, duration: 60, useNativeDriver: true }),
    ]).start();
  };

  const tryUnlock = (pin: string) => {
    const ok = hashPin(pin, settings.pinHash?.salt).hash === settings.pinHash?.hash;
    if (ok) {
      setLocked(false);
      setValue("");
    } else {
      doShake();
      setValue("");
    }
  };

  const forgot = async () => {
    const ok = await confirm(t("st.forgotPin"), t("st.resetPinConfirm"));
    if (ok) {
      updateSettings({ pinHash: undefined });
      setLocked(false);
    }
  };

  return (
    <View style={[lockStyles.root, { backgroundColor: C.canvas }]}>
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", gap: 14 }}>
        <View style={[lockStyles.logo, { backgroundColor: C.brand }]}>
          <Icon name="phone" size={30} color={C.onBrand} />
        </View>
        <Text style={{ fontSize: F.title, fontWeight: "800", color: C.ink }}>callmony</Text>
        <View style={{ flexDirection: "row", gap: 14, marginTop: 6 }}>
          {[0, 1, 2, 3].map((i) => (
            <View
              key={i}
              style={[lockStyles.dot, { backgroundColor: value.length > i ? C.brand : C.line }]}
            />
          ))}
        </View>
        <Text style={{ fontSize: F.small, color: C.sub }}>{t("st.enterPin")}</Text>
        <Pressable onPress={forgot} hitSlop={8}>
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("st.forgotPin")}</Text>
        </Pressable>
      </View>
      <Animated.View style={{ transform: [{ translateX: shake }], paddingBottom: 24 }}>
        <PinPad value={value} onChange={(v) => { setValue(v); if (v.length === 4) setTimeout(() => tryUnlock(v), 120); }} />
      </Animated.View>
    </View>
  );
};

/* ---------------- PIN pad ---------------- */

const PinPad: React.FC<{
  value: string;
  onChange: (v: string) => void;
}> = ({ value, onChange }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const press = (d: string) => {
    if (value.length >= 4) return;
    onChange(value + d);
  };
  const keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del"];
  return (
    <View style={{ paddingHorizontal: 24 }}>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 14, justifyContent: "center" }}>
        {keys.map((k, i) => {
          if (k === "") return <View key={i} style={{ width: 68, height: 56 }} />;
          return (
            <Pressable
              key={i}
              onPress={() => (k === "del" ? onChange(value.slice(0, -1)) : press(k))}
              android_ripple={{ color: "rgba(128,128,128,0.25)", borderless: true, radius: 34 }}
              style={[pinStyles.key, { backgroundColor: C.card, borderColor: C.line }]}
            >
              {k === "del" ? (
                <Icon name="backspace-outline" size={24} color={C.sub} />
              ) : (
                <Text style={{ fontSize: 24, fontWeight: "600", color: C.ink }}>{k}</Text>
              )}
            </Pressable>
          );
        })}
      </View>
    </View>
  );
};

const setStyles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: 12, paddingVertical: 4 },
  pill: { borderRadius: 999, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 7, minHeight: 36, justifyContent: "center" },
});

const pinStyles = StyleSheet.create({
  key: {
    width: 68,
    height: 56,
    borderRadius: 14,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});

const lockStyles = StyleSheet.create({
  root: { position: "absolute", top: 0, left: 0, right: 0, bottom: 0, zIndex: 85, elevation: 85 },
  logo: { width: 62, height: 62, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  dot: { width: 14, height: 14, borderRadius: 7 },
});
