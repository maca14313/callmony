/* ContactDetail — compact navigation-bar header (avatar · name · accurate
   number · star · pencil · ⋯), webapp 6×2 quick-action grid (12 borderless
   tiles), horizontally scrollable segmented tabs (Ledger · Lists · Notes ·
   Bank · Reminders · Info), and a single vertical ScrollView so the whole
   top area scrolls. The Info tab carries phones (per-number options),
   emails, UNLIMITED socials (add as many as you want), addresses, birthday,
   work, notes and the per-contact CALL HISTORY (tap a row to call back).
   Every button works: Telegram opens a saved handle or offers to add one,
   More shares/copies/dials, the pencil opens the IN-APP contact editor. */

import React, { useMemo, useState } from "react";
import { Platform, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import Icon from "react-native-vector-icons/MaterialCommunityIcons";
import { useApp } from "../lib/store";
import { useUI, type Overlay } from "../lib/ui-store";
import { useT } from "../i18n";
import { ownerFullBalance, openListsPending } from "../lib/calc";
import type { DeviceContact, SocialLink } from "../lib/types";
import { displayName, fd, ft, durationStr, money, normalizePhone, prettyPhone, copyText, relTime } from "../lib/utils";
import { contactOwnerId } from "../lib/types";
import { placeCall } from "../native/calls";
import { openURL, openWithFeedback, smsLink, tgLink, waLink, reminderTemplates, fillTemplate, socialUrl, socialLinkFromNumber, NUMBER_SOCIAL_APPS, appTakesNumber, mailtoLink, primaryPhone } from "../lib/share";
import { shareContactVcard } from "../lib/exporters";
import { F, useThemeColors } from "../theme";
import { Avatar, Btn, Card, Chip, Field, Money, Segmented, toast } from "../components/kit";
import { Sheet, BottomSheet } from "../components/Sheet";
import { LedgerSection } from "../sections/LedgerSection";
import { ListsSection } from "../sections/ListsSection";
import { NotesSection } from "../sections/NotesSection";
import { BankSection } from "../sections/BankSection";
import { RemindersSection } from "../sections/RemindersSection";
import { NumberPicker } from "../components/ContactPicker";
import { ContactEditor } from "./ContactEditor";

type Seg = "ledger" | "lists" | "notes" | "bank" | "reminders" | "info";

const SOCIAL_APPS: { key: SocialLink["app"]; label: string; icon: string }[] = [
  { key: "telegram", label: "Telegram", icon: "send" },
  { key: "whatsapp", label: "WhatsApp", icon: "whatsapp" },
  { key: "signal", label: "Signal", icon: "shield-half-full" },
  { key: "viber", label: "Viber", icon: "phone-classic" },
  { key: "instagram", label: "Instagram", icon: "instagram" },
  { key: "x", label: "X", icon: "twitter" },
  { key: "other", label: "Other", icon: "link-variant" },
];

const socialIcon = (app: SocialLink["app"]): string =>
  SOCIAL_APPS.find((a) => a.key === app)?.icon ?? "link-variant";

export const ContactDetailOverlay: React.FC<{ params: Extract<Overlay, { type: "contact" }> }> = ({ params }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const pop = useUI((s) => s.pop);
  const contact = useApp((s) => s.deviceContacts.find((c) => c.recordID === params.recordID));
  const entries = useApp((s) => s.entries);
  const lists = useApp((s) => s.lists);
  const contactX = useApp((s) => s.contactX);
  const countryCode = useApp((s) => s.settings.countryCode);
  const logMessage = useApp((s) => s.logMessage);
  const [seg, setSeg] = useState<Seg>(params.initialSeg ?? "ledger");
  const [numPicker, setNumPicker] = useState<{ action: "call" | "sms" | "whatsapp" } | null>(null);
  const [moreOpen, setMoreOpen] = useState(false);
  const [socialSheet, setSocialSheet] = useState<{ app: SocialLink["app"] } | null>(null);
  const [phoneSheet, setPhoneSheet] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  /* v1.6.1 — unified choosers: every contact action tile opens a picker with
     add/edit rows (user request): telegram → handle/number chooser, email →
     address chooser, and a dedicated “＋ add to this contact” tile. */
  const [tgPicker, setTgPicker] = useState(false);
  const [emailPicker, setEmailPicker] = useState(false);
  const [addSheet, setAddSheet] = useState(false);

  const ownerId = contact ? contactOwnerId(contact.recordID) : null;
  const xEntry = contactX.find((x) => x.recordID === params.recordID);
  const socials = xEntry?.socials ?? [];
  const isFav = !!xEntry?.favorite;
  const balance = ownerId ? ownerFullBalance(entries, lists, ownerId) : 0;
  const pendingLists = ownerId ? openListsPending(lists, ownerId) : 0;
  const phone = contact ? primaryPhone(contact) : undefined;
  const email = contact?.emails[0]?.address;

  if (!contact) {
    return (
      <Sheet title={t("cd.notFound")} subtitle={t("cd.notFoundHint")} onBack={pop}>
        <Text style={{ color: C.sub, fontSize: F.body, textAlign: "center", paddingVertical: 30 }}>{t("cd.notFoundHint")}</Text>
      </Sheet>
    );
  }

  /* ---------- multi-number routing — v1.6.1: the chooser opens for EVERY
     number-based action (call / sms / whatsapp), single number or not. The
     sheet always carries the “Add or edit numbers” row, so adding or fixing
     numbers is reachable from every tile (user request: “unify: tapping
     message/social/+ opens a chooser sheet with add/edit options”). ---------- */
  const runAction = (action: "call" | "sms" | "whatsapp") => {
    const unique = dedupePhones(contact!.phones, countryCode);
    if (!unique.length) {
      toast(t("cd.noPhone"), "error");
      return;
    }
    setNumPicker({ action });
  };

  const fireAction = (action: "call" | "sms" | "whatsapp", number?: string) => {
    if (!number) {
      toast(t("cd.noPhone"), "error");
      return;
    }
    if (action === "call") placeCall(number);
    if (action === "sms") openWithFeedback(smsLink(number), t("act.couldntOpen"));
    if (action === "whatsapp") {
      const text = balance > 0
        ? fillTemplate(reminderTemplates[0].body, {
            name: displayName(contact),
            amount: money(Math.abs(balance)),
            date: fd(Date.now()),
          })
        : balance < 0
          ? `Hi ${displayName(contact)}, I'd like to settle up ${money(Math.abs(balance))} that I owe you. How would you like to receive it?`
          : undefined;
      if (ownerId) logMessage({ ownerId, channel: "whatsapp", text: text ?? "" });
      openWithFeedback(waLink(number, text, countryCode), t("act.couldntOpen"));
    }
    setNumPicker(null);
  };

  /* ---------- Telegram: the chooser lists saved handles AND every number
     (a tg: link is built from it), plus add-handle / add-edit-numbers rows.
     Same interaction as the call tile — the unified pattern the user asked
     for twice. ---------- */
  const fireTelegram = () => setTgPicker(true);

  /* ---------- Email: chooser over every address + add/edit ---------- */
  const fireEmail = () => setEmailPicker(true);

  /* ---------- quick actions — the webapp 6×2 grid ---------- */
  const quickActions: { icon: string; label: string; run: () => void; disabled?: boolean }[] = [
    { icon: "phone", label: t("act.call"), run: () => runAction("call"), disabled: !contact.phones.length },
    { icon: "message-text-outline", label: t("act.sms"), run: () => runAction("sms"), disabled: !contact.phones.length },
    { icon: "whatsapp", label: t("act.whatsapp"), run: () => runAction("whatsapp"), disabled: !contact.phones.length },
    { icon: "send", label: t("act.telegram"), run: fireTelegram },
    { icon: "email-outline", label: t("act.email"), run: fireEmail },
    { icon: "book-outline", label: t("tab.ledger"), run: () => setSeg("ledger") },
    { icon: "bank-outline", label: t("tab.bank"), run: () => setSeg("bank") },
    { icon: "format-list-checks", label: t("tab.lists"), run: () => setSeg("lists") },
    { icon: "note-text-outline", label: t("tab.notes"), run: () => setSeg("notes") },
    { icon: "bell-outline", label: t("tab.reminders"), run: () => setSeg("reminders") },
    { icon: "information-outline", label: t("tab.info"), run: () => setSeg("info") },
    /* v1.6.1 — the “＋” tile (replaces the More tile; the header ⋯ still
       opens the More sheet): an add/edit chooser for this contact */
    { icon: "plus", label: t("cd.addToContact"), run: () => setAddSheet(true) },
  ];

  const toggleFav = () => {
    useApp.getState().toggleFavorite(contact.recordID);
    toast(isFav ? t("cd.removedFromFavorites") : t("cd.addToFavorites"), "ok");
  };

  const copyNumber = () =>
    phone &&
    copyText(phone).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error"));

  /* v1.5.4 — the IN-APP editor replaces the OS external contact editor */
  const editContact = () => setEditorOpen(true);

  return (
    <Sheet
      title={displayName(contact)}
      subtitle={phone ? prettyPhone(phone, countryCode) : contact.company || t("cd.noPhone")}
      onBack={pop}
      headerLeft={<Avatar contact={contact} size={34} />}
      onSubtitleLongPress={copyNumber}
      headerRight={
        <View style={{ flexDirection: "row", alignItems: "center" }}>
          <Pressable
            onPress={editContact}
            hitSlop={6}
            accessibilityLabel={t("cd.editContact")}
            style={{ width: 30, height: 38, alignItems: "center", justifyContent: "center" }}
          >
            <Icon name="pencil-outline" size={20} color={C.sub} />
          </Pressable>
          <Pressable
            onPress={toggleFav}
            hitSlop={6}
            accessibilityLabel={isFav ? t("cd.removeFavorite") : t("cd.addFavorite")}
            style={{ width: 34, height: 38, alignItems: "center", justifyContent: "center" }}
          >
            <Icon name={isFav ? "star" : "star-outline"} size={20} color={isFav ? "#b97d10" : C.sub} />
          </Pressable>
          <Pressable
            onPress={() => setMoreOpen(true)}
            hitSlop={6}
            accessibilityLabel={t("cd.moreActions")}
            style={{ width: 30, height: 38, alignItems: "center", justifyContent: "center" }}
          >
            <Icon name="dots-vertical" size={20} color={C.sub} />
          </Pressable>
        </View>
      }
      scroll={false}
    >
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 10, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
      >
        {/* balance line — the ONLY hero remnant (user request): the name,
            number, avatar, star and ⋯ live in the nav bar above; here it is
            just the balance + owes status, compact and centered */}
        <View style={cdStyles.balanceLine}>
          <View
            style={[
              cdStyles.balancePill,
              {
                backgroundColor:
                  Math.abs(balance) < 0.005 ? C.cardAlt : balance > 0 ? "rgba(22,160,133,0.14)" : "rgba(214,69,69,0.12)",
                borderColor: C.line,
                marginTop: 0,
              },
            ]}
          >
            <Icon
              name={balance > 0.005 ? "arrow-bottom-left" : balance < -0.005 ? "arrow-top-right" : "scale-balance"}
              size={13}
              color={balance > 0.005 ? C.green : balance < -0.005 ? C.red : C.sub}
            />
            <Money value={balance} signed colorize size={15} weight="800" fit compact />
          </View>
          <Text style={{ fontSize: F.small, color: C.sub, fontWeight: "600" }}>
            {balance > 0.005 ? t("cd.theyOweYou") : balance < -0.005 ? t("cd.youOweThem") : t("cd.settledUp")}
          </Text>
          {pendingLists > 0.005 && (
            <Text style={{ fontSize: 11, color: C.faint }}>
              ({t("lg.openLists")}: <Money value={pendingLists} signed size={11} weight="600" compact />)
            </Text>
          )}
        </View>

        {/* quick actions — 6 per row × 2 rows, borderless (webapp grid) */}
        <View style={{ gap: 3, marginBottom: 8 }}>
          {[0, 1].map((row) => (
            <View key={row} style={{ flexDirection: "row", gap: 3 }}>
              {quickActions.slice(row * 6, row * 6 + 6).map((a) => (
                <Pressable
                  key={a.label}
                  disabled={a.disabled}
                  onPress={a.run}
                  android_ripple={{ color: "rgba(128,128,128,0.16)", borderless: true, radius: 36 }}
                  style={({ pressed }) => [cdStyles.tile, { opacity: a.disabled ? 0.4 : pressed ? 0.65 : 1 }]}
                  accessibilityLabel={a.label}
                >
                  <Icon name={a.icon} size={22} color={a.disabled ? C.faint : C.brand} />
                  <Text
                    style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700", textAlign: "center" }}
                    numberOfLines={1}
                    adjustsFontSizeToFit
                    minimumFontScale={0.6}
                  >
                    {a.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          ))}
        </View>

        {/* segmented tabs — single-row side-scroll, compact */}
        <Segmented
          scroll
          value={seg}
          onChange={(k) => setSeg(k as Seg)}
          options={[
            { key: "ledger", label: t("tab.ledger") },
            { key: "lists", label: t("tab.lists") },
            { key: "notes", label: t("tab.notes") },
            { key: "bank", label: t("tab.bank") },
            { key: "reminders", label: t("tab.reminders") },
            { key: "info", label: t("tab.info") },
          ]}
        />

        <View style={{ marginTop: 12 }}>
          {seg === "ledger" && ownerId && <LedgerSection ownerId={ownerId} />}
          {seg === "lists" && ownerId && <ListsSection ownerId={ownerId} />}
          {seg === "notes" && ownerId && <NotesSection ownerId={ownerId} />}
          {seg === "bank" && ownerId && <BankSection ownerId={ownerId} />}
          {seg === "reminders" && ownerId && <RemindersSection ownerId={ownerId} />}
          {seg === "info" && (
            <ContactInfo
              contact={contact}
              socials={socials}
              onPhoneOptions={(number) => setPhoneSheet(number)}
              onAddSocial={(app) => setSocialSheet({ app })}
              onRemoveSocial={(id) => useApp.getState().removeSocial(contact.recordID, id)}
              onEdit={() => setEditorOpen(true)}
            />
          )}
        </View>
      </ScrollView>

      {/* number picker (deduped) — trailing row adds/edits numbers in-app */}
      {numPicker && (
        <NumberPicker
          recordID={contact.recordID}
          onPick={(number) => fireAction(numPicker.action, number)}
          onClose={() => setNumPicker(null)}
          onAddEditNumbers={() => {
            setNumPicker(null);
            setEditorOpen(true);
          }}
        />
      )}

      {/* telegram chooser — saved handles + every number + add rows */}
      {tgPicker && (
        <TelegramPicker
          socials={socials.filter((s) => s.app === "telegram")}
          numbers={dedupePhones(contact.phones, countryCode).map((p) => p.number)}
          countryCode={countryCode}
          onClose={() => setTgPicker(false)}
          onAddHandle={() => {
            setTgPicker(false);
            setSocialSheet({ app: "telegram" });
          }}
          onAddEditNumbers={() => {
            setTgPicker(false);
            setEditorOpen(true);
          }}
        />
      )}

      {/* email chooser — every address + add/edit */}
      {emailPicker && (
        <EmailPicker
          emails={contact.emails.map((e) => e.address)}
          onClose={() => setEmailPicker(false)}
          onAddEditEmails={() => {
            setEmailPicker(false);
            setEditorOpen(true);
          }}
        />
      )}

      {/* ＋ add-to-contact chooser */}
      {addSheet && (
        <AddToContactSheet
          onClose={() => setAddSheet(false)}
          onAddPhone={() => {
            setAddSheet(false);
            setEditorOpen(true);
          }}
          onAddEmail={() => {
            setAddSheet(false);
            setEditorOpen(true);
          }}
          onAddSocial={() => {
            setAddSheet(false);
            setSocialSheet({ app: "telegram" });
          }}
          onEdit={() => {
            setAddSheet(false);
            setEditorOpen(true);
          }}
        />
      )}

      {/* header ⋯ / More tile — one shared sheet */}
      {moreOpen && (
        <ContactMoreSheet
          onClose={() => setMoreOpen(false)}
          name={displayName(contact)}
          phone={phone}
          email={email}
          onEdit={editContact}
          onCopy={copyNumber}
          onShareVCard={() => shareContactVcard(contact)}
          onAddSocial={(app) => setSocialSheet({ app })}
        />
      )}

      {/* add-social sheet — as many socials as the user wants */}
      {socialSheet && (
        <AddSocialSheet
          initialApp={socialSheet.app}
          phoneNumber={phone}
          countryCode={countryCode}
          onClose={() => setSocialSheet(null)}
          onSave={(app, handle) => {
            useApp.getState().addSocial(contact.recordID, app, handle);
            toast(t("ce.addSocial") + " ✓", "ok");
          }}
        />
      )}

      {/* per-phone options */}
      {phoneSheet && (
        <PhoneOptionsSheet
          number={phoneSheet}
          countryCode={countryCode}
          isPrimary={phone?.replace(/\D/g, "") === phoneSheet.replace(/\D/g, "")}
          onClose={() => setPhoneSheet(null)}
          onSetPrimary={() => {
            useApp.getState().setPrimaryPhone(contact.recordID, phoneSheet);
            toast(t("cd.primaryUpdated"), "ok");
          }}
        />
      )}

      {/* in-app contact editor (nav pencil · More → Edit · Info → Edit) */}
      {editorOpen && <ContactEditor recordID={contact.recordID} onClose={() => setEditorOpen(false)} />}
    </Sheet>
  );
};

/* ---------- dedupe helper — the same number stored in several formats
             (09…, +251…, duplicated rows) appears ONCE ---------- */
const dedupePhones = (
  phones: { id: string; label: string; number: string }[],
  countryCode: string
): { id: string; label: string; number: string }[] => {
  const seen = new Set<string>();
  const out: { id: string; label: string; number: string }[] = [];
  phones.forEach((p) => {
    const key = normalizePhone(p.number, countryCode).replace(/\D/g, "") || p.number.replace(/\D/g, "");
    if (seen.has(key)) return;
    seen.add(key);
    out.push(p);
  });
  return out;
};

/* ---------------- more sheet (header ⋯ + More tile) ---------------- */

const ContactMoreSheet: React.FC<{
  onClose: () => void;
  name: string;
  phone?: string;
  email?: string;
  onEdit: () => void;
  onCopy: () => void;
  onShareVCard: () => void;
  onAddSocial: (app: SocialLink["app"]) => void;
}> = ({ onClose, name, phone, email, onEdit, onCopy, onShareVCard, onAddSocial }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);

  const items: { icon: string; label: string; sub?: string; run: () => void; disabled?: boolean }[] = [
    { icon: "pencil-outline", label: t("cd.editContact"), run: onEdit },
    { icon: "share-variant-outline", label: t("cd.shareVcard"), run: onShareVCard },
    {
      icon: "email-outline",
      label: t("act.email"),
      sub: email,
      run: () => email && openWithFeedback(mailtoLink(email), t("act.couldntOpen")),
      disabled: !email,
    },
    {
      icon: "content-copy",
      label: t("cd.copyNumber"),
      sub: phone ? prettyPhone(phone, countryCode) : undefined,
      run: onCopy,
      disabled: !phone,
    },
    {
      icon: "dialpad",
      label: t("cd.openInDialer"),
      run: () => phone && openWithFeedback(`tel:${encodeURIComponent(phone)}`, t("act.couldntOpen")),
      disabled: !phone,
    },
    { icon: "link-plus", label: t("ce.addSocial"), run: () => onAddSocial("telegram") },
  ];

  return (
    <BottomSheet title={name} onClose={onClose}>
      <View style={{ gap: 4, paddingBottom: 10 }}>
        {items.map((it) => (
          <Pressable
            key={it.label}
            disabled={it.disabled}
            android_ripple={{ color: "rgba(128,128,128,0.12)" }}
            onPress={() => {
              onClose();
              setTimeout(it.run, 180);
            }}
            /* v1.6.1 — long-press copies the email/number shown in the row
               (user request: "long-press copies numbers/emails wherever it
               is, even in the options") */
            onLongPress={() =>
              it.sub &&
              copyText(it.sub).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
            }
            delayLongPress={400}
            style={({ pressed }) => [
              cdStyles.menuItem,
              { backgroundColor: pressed ? C.chip : "transparent", opacity: it.disabled ? 0.4 : 1 },
            ]}
          >
            <Icon name={it.icon} size={21} color={C.sub} />
            <View style={{ flex: 1, minWidth: 0 }}>
              <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }} numberOfLines={1}>
                {it.label}
              </Text>
              {!!it.sub && (
                <Text style={{ fontSize: F.tiny, color: C.faint }} numberOfLines={1}>
                  {it.sub}
                </Text>
              )}
            </View>
          </Pressable>
        ))}
      </View>
    </BottomSheet>
  );
};

/* ---------------- add-social sheet ---------------- */

const AddSocialSheet: React.FC<{
  initialApp?: SocialLink["app"];
  phoneNumber?: string;
  countryCode: string;
  onClose: () => void;
  onSave: (app: SocialLink["app"], handle: string) => void;
}> = ({ initialApp = "telegram", phoneNumber, countryCode, onClose, onSave }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const [app, setApp] = useState<SocialLink["app"]>(initialApp);
  const [handle, setHandle] = useState(() =>
    /* number-based apps pre-fill with the contact's own number — one tap
       saves a working link built from that number */
    appTakesNumber(initialApp) && phoneNumber ? prettyPhone(phoneNumber, countryCode) : ""
  );

  /* switching app re-prefills only when the field is empty or still holds
     the previous auto number — never wipes something the user typed */
  const pickApp = (key: SocialLink["app"]) => {
    const auto = appTakesNumber(key) && phoneNumber ? prettyPhone(phoneNumber, countryCode) : "";
    const wasAuto =
      !handle.trim() ||
      (phoneNumber && handle.replace(/\D/g, "") === phoneNumber.replace(/\D/g, ""));
    setApp(key);
    if (wasAuto) setHandle(auto);
  };

  const save = () => {
    const h = handle.trim();
    if (!h) {
      toast(t("ce.socialPlaceholder"), "error");
      return;
    }
    onSave(app, h);
    onClose();
  };

  const numberBased = appTakesNumber(app);

  return (
    <BottomSheet title={t("ce.addSocial")} onClose={onClose}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 6, paddingVertical: 4 }} style={{ flexGrow: 0 }}>
        {SOCIAL_APPS.map((a) => (
          <Chip key={a.key} label={a.label} icon={a.icon} active={app === a.key} onPress={() => pickApp(a.key)} />
        ))}
      </ScrollView>
      {!!numberBased && !!phoneNumber && (
        <Text style={{ fontSize: F.tiny, color: C.sub, marginTop: 8, marginBottom: -2 }}>
          {t("ce.socialFromNumber")}
        </Text>
      )}
      <View style={{ marginTop: 10 }}>
        <Field
          label={t("cd.socials")}
          value={handle}
          onChangeText={setHandle}
          placeholder={t("ce.socialPlaceholder")}
          autoFocus
          onSubmitEditing={save}
        />
      </View>
      <Btn label={t("act.save")} icon="check" onPress={save} disabled={!handle.trim()} style={{ marginTop: 4, marginBottom: 6 }} />
    </BottomSheet>
  );
};

/* ---------------- per-phone options ---------------- */

const PhoneOptionsSheet: React.FC<{
  number: string;
  countryCode: string;
  isPrimary: boolean;
  onClose: () => void;
  onSetPrimary: () => void;
}> = ({ number, countryCode, isPrimary, onClose, onSetPrimary }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();

  const items: { icon: string; label: string; run: () => void }[] = [
    { icon: "phone", label: t("act.call"), run: () => placeCall(number) },
    { icon: "whatsapp", label: t("act.whatsapp"), run: () => openURL(waLink(number, undefined, countryCode)) },
    { icon: "message-text-outline", label: t("act.sms"), run: () => openURL(smsLink(number)) },
    {
      icon: "content-copy",
      label: t("cd.copyNumber"),
      run: () => copyText(number).then((ok) => toast(ok ? t("cd.numberCopied") : t("act.couldntCopy"), ok ? "ok" : "error")),
    },
    ...(isPrimary
      ? []
      : [{ icon: "star-outline", label: t("ce.setPrimaryNumber"), run: onSetPrimary }]),
  ];

  return (
    <BottomSheet title={prettyPhone(number, countryCode)} onClose={onClose}>
      <View style={{ gap: 4, paddingBottom: 10 }}>
        {items.map((it) => (
          <Pressable
            key={it.label}
            android_ripple={{ color: "rgba(128,128,128,0.12)" }}
            onPress={() => {
              onClose();
              setTimeout(it.run, 180);
            }}
            /* v1.6.1 — long-press ANY option row to copy the number (user
               request: “long-press copies numbers wherever it is, even in the
               options”) */
            onLongPress={() =>
              copyText(number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
            }
            delayLongPress={400}
            style={({ pressed }) => [cdStyles.menuItem, { backgroundColor: pressed ? C.chip : "transparent" }]}
          >
            <Icon name={it.icon} size={21} color={C.sub} />
            <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600", flexShrink: 1 }} numberOfLines={1}>
              {it.label}
            </Text>
          </Pressable>
        ))}
      </View>
    </BottomSheet>
  );
};

/* ---------------- v1.6.1 unified choosers ---------------- */

/** shared row for the chooser sheets — long-press copies the value */
const ChooserRow: React.FC<{
  icon: string;
  title: string;
  sub?: string;
  copyValue?: string;
  onPress: () => void;
}> = ({ icon, title, sub, copyValue, onPress }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  return (
    <Pressable
      android_ripple={{ color: "rgba(128,128,128,0.14)" }}
      onPress={onPress}
      onLongPress={() =>
        copyValue &&
        copyText(copyValue).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
      }
      delayLongPress={400}
      style={[cdStyles.row, { borderColor: C.line, backgroundColor: C.card }]}
      accessibilityLabel={title}
    >
      <View style={[cdStyles.socialIcon, { backgroundColor: C.brandSoft }]}>
        <Icon name={icon} size={16} color={C.brand} />
      </View>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={{ fontSize: F.body, fontWeight: "700", color: C.ink }} numberOfLines={1}>
          {title}
        </Text>
        {!!sub && (
          <Text style={{ fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
            {sub}
          </Text>
        )}
      </View>
      <Icon name="chevron-right" size={16} color={C.faint} />
    </Pressable>
  );
};

/** trailing dashed “add / edit” row shared by every chooser */
const ChooserAddRow: React.FC<{ icon: string; label: string; onPress: () => void }> = ({ icon, label, onPress }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Pressable
      android_ripple={{ color: "rgba(128,128,128,0.14)" }}
      onPress={onPress}
      style={[cdStyles.addRow, { borderColor: C.line }]}
      accessibilityLabel={label}
    >
      <Icon name={icon} size={16} color={C.brand} />
      <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{label}</Text>
    </Pressable>
  );
};

/** Telegram — saved handles, a link per number, add-handle + add/edit rows */
const TelegramPicker: React.FC<{
  socials: SocialLink[];
  numbers: string[];
  countryCode: string;
  onClose: () => void;
  onAddHandle: () => void;
  onAddEditNumbers: () => void;
}> = ({ socials, numbers, countryCode, onClose, onAddHandle, onAddEditNumbers }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  return (
    <BottomSheet title={t("act.telegram")} onClose={onClose}>
      <View style={{ gap: 6, paddingBottom: 10 }}>
        {socials.map((s) => (
          <ChooserRow
            key={s.id}
            icon="send"
            title={s.handle}
            sub="Telegram"
            copyValue={s.handle}
            onPress={() => {
              onClose();
              setTimeout(() => openWithFeedback(tgLink(s.handle), t("act.couldntOpen")), 160);
            }}
          />
        ))}
        {numbers.map((n) => {
          const url = socialLinkFromNumber("telegram", n, countryCode);
          if (!url) return null;
          return (
            <ChooserRow
              key={n}
              icon="phone"
              title={prettyPhone(n, countryCode)}
              sub={t("ce.fromNumber")}
              copyValue={n}
              onPress={() => {
                onClose();
                setTimeout(() => openWithFeedback(url, t("act.couldntOpen")), 160);
              }}
            />
          );
        })}
        {!socials.length && !numbers.length && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 14, fontSize: F.small }}>
            {t("cd.noPhone")}
          </Text>
        )}
        <ChooserAddRow icon="plus" label={t("ce.addSocial")} onPress={onAddHandle} />
        <ChooserAddRow icon="pencil-outline" label={t("cd.addOrEditNumbers")} onPress={onAddEditNumbers} />
      </View>
    </BottomSheet>
  );
};

/** Email — one row per address + add/edit */
const EmailPicker: React.FC<{
  emails: string[];
  onClose: () => void;
  onAddEditEmails: () => void;
}> = ({ emails, onClose, onAddEditEmails }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  return (
    <BottomSheet title={t("cd.chooseEmail")} onClose={onClose}>
      <View style={{ gap: 6, paddingBottom: 10 }}>
        {emails.map((e) => (
          <ChooserRow
            key={e}
            icon="email-outline"
            title={e}
            copyValue={e}
            onPress={() => {
              onClose();
              setTimeout(() => openWithFeedback(mailtoLink(e), t("act.couldntOpen")), 160);
            }}
          />
        ))}
        {!emails.length && (
          <Text style={{ color: C.sub, textAlign: "center", paddingVertical: 14, fontSize: F.small }}>
            {t("cd.noEmail")}
          </Text>
        )}
        <ChooserAddRow icon="plus" label={t("ce.addEmail")} onPress={onAddEditEmails} />
      </View>
    </BottomSheet>
  );
};

/** ＋ add-to-contact — which kind of thing to add (or edit everything) */
const AddToContactSheet: React.FC<{
  onClose: () => void;
  onAddPhone: () => void;
  onAddEmail: () => void;
  onAddSocial: () => void;
  onEdit: () => void;
}> = ({ onClose, onAddPhone, onAddEmail, onAddSocial, onEdit }) => {
  const t = useT();
  return (
    <BottomSheet title={t("cd.addToContact")} onClose={onClose}>
      <View style={{ gap: 6, paddingBottom: 10 }}>
        <ChooserRow icon="phone-plus" title={t("ce.addPhone")} onPress={onAddPhone} />
        <ChooserRow icon="email-plus-outline" title={t("ce.addEmail")} onPress={onAddEmail} />
        <ChooserRow icon="link-plus" title={t("ce.addSocial")} onPress={onAddSocial} />
        <ChooserAddRow icon="pencil-outline" label={t("cd.editContact")} onPress={onEdit} />
      </View>
    </BottomSheet>
  );
};

/* ---------------- info tab ---------------- */

const ContactInfo: React.FC<{
  contact: DeviceContact;
  socials: SocialLink[];
  onPhoneOptions: (number: string) => void;
  onAddSocial: (app: SocialLink["app"]) => void;
  onRemoveSocial: (id: string) => void;
  onEdit: () => void;
}> = ({ contact, socials, onPhoneOptions, onAddSocial, onRemoveSocial, onEdit }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  const t = useT();
  const countryCode = useApp((s) => s.settings.countryCode);
  const systemCalls = useApp((s) => s.systemCalls);
  const appRecents = useApp((s) => s.recents);
  const ownerId = contactOwnerId(contact.recordID);
  const primary = primaryPhone(contact);

  /* per-contact call history — Android: real system log matched on any of
     the contact's numbers; iOS: the app's own call log for this owner.
     Compact: the 10 most recent, tap a row to call back. */
  const history = useMemo(() => {
    if (Platform.OS === "android") {
      const numbers = new Set(
        contact.phones.map((p) => normalizePhone(p.number, countryCode).replace(/\D/g, "")).filter((d) => d.length >= 5)
      );
      return systemCalls
        .filter((c) => {
          const d = normalizePhone(c.phoneNumber ?? "", countryCode).replace(/\D/g, "");
          return d.length >= 5 && numbers.has(d);
        })
        .slice(0, 10);
    }
    return appRecents.filter((r) => r.ownerId === ownerId).slice(0, 10);
  }, [contact.phones, countryCode, systemCalls, appRecents, ownerId]);

  return (
    <View>
      {/* call history */}
      <InfoBlock title={t("cd.callHistory")}>
        {history.length === 0 ? (
          <Muted text={t("cd.noCallsHint")} />
        ) : (
          <View style={{ gap: 2 }}>
            {history.map((h) => {
              const at = "timestamp" in h ? h.timestamp : h.at;
              const number = "phoneNumber" in h ? h.phoneNumber : h.phone;
              const dir =
                h.direction === "missed"
                  ? { icon: "phone-missed", color: C.red, label: t("cd.missed") }
                  : h.direction === "incoming"
                    ? { icon: "phone-incoming", color: C.green, label: t("cd.incoming") }
                    : { icon: "phone-outgoing", color: C.brand, label: t("cd.outgoing") };
              return (
                <Pressable
                  key={h.id}
                  onPress={() => number && placeCall(number)}
                  android_ripple={{ color: "rgba(128,128,128,0.1)" }}
                  style={({ pressed }) => [cdStyles.historyRow, { opacity: pressed ? 0.65 : 1 }]}
                  accessibilityLabel={`${dir.label} ${ft(at)}`}
                >
                  <Icon name={dir.icon} size={15} color={dir.color} />
                  <Text style={{ flex: 1, fontSize: F.tiny, color: C.sub }} numberOfLines={1}>
                    {dir.label} · {relTime(at)} · {ft(at)}
                    {h.duration > 0 ? ` · ${durationStr(h.duration)}` : ""}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        )}
      </InfoBlock>

      {!!contact.birthday && (
        <Card style={{ marginBottom: 10 }}>
          <Text style={{ fontSize: F.small, color: C.sub }}>🎂 {t("cd.birthday")}: {contact.birthday}</Text>
        </Card>
      )}

      {/* phones */}
      <InfoBlock title={t("cd.phones")}>
        {dedupePhones(contact.phones, countryCode).map((p) => {
          const isPrimary = primary?.replace(/\D/g, "") === p.number.replace(/\D/g, "");
          return (
            <View key={p.id || p.number} style={cdStyles.infoRow}>
              <Pressable
                style={{ flex: 1, minWidth: 0 }}
                onPress={() => onPhoneOptions(p.number)}
                onLongPress={() =>
                  copyText(p.number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
                }
                delayLongPress={400}
              >
                <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                  <View style={[cdStyles.labelChip, { backgroundColor: C.chip }]}>
                    <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700" }}>{p.label}</Text>
                  </View>
                  <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600", flexShrink: 1 }} numberOfLines={1}>
                    {prettyPhone(p.number, countryCode)}
                  </Text>
                  {isPrimary && <Icon name="star" size={12} color="#b97d10" />}
                  <Icon name="chevron-right" size={14} color={C.faint} />
                </View>
              </Pressable>
              <Pressable
                onPress={() =>
                  copyText(p.number).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
                }
                hitSlop={6}
                style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}
                accessibilityLabel={`${t("act.copy")} ${p.number}`}
              >
                <Icon name="content-copy" size={15} color={C.brand} />
              </Pressable>
              <Pressable
                onPress={() => placeCall(p.number)}
                hitSlop={6}
                style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}
                accessibilityLabel={t("act.call")}
              >
                <Icon name="phone" size={15} color={C.brand} />
              </Pressable>
            </View>
          );
        })}
        {contact.phones.length === 0 && <Muted text={t("cd.noPhone")} />}
      </InfoBlock>

      {/* emails */}
      <InfoBlock title={t("cd.emails")}>
        {contact.emails.map((e) => (
          <View key={e.id} style={cdStyles.infoRow}>
            <Pressable
              style={{ flex: 1, minWidth: 0 }}
              onPress={() =>
                copyText(e.address).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
              }
              onLongPress={() =>
                copyText(e.address).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
              }
              delayLongPress={400}
            >
              <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
                <View style={[cdStyles.labelChip, { backgroundColor: C.chip }]}>
                  <Text style={{ fontSize: F.tiny, color: C.sub, fontWeight: "700" }}>{e.label}</Text>
                </View>
                <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600", flexShrink: 1 }} numberOfLines={1}>
                  {e.address}
                </Text>
              </View>
            </Pressable>
            <Pressable
              onPress={() => openWithFeedback(mailtoLink(e.address), t("act.couldntOpen"))}
              hitSlop={6}
              style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}
              accessibilityLabel={`email ${e.address}`}
            >
              <Icon name="email-outline" size={15} color={C.brand} />
            </Pressable>
          </View>
        ))}
        {contact.emails.length === 0 && <Muted text={t("cd.noEmail")} />}
      </InfoBlock>

      {/* socials — saved links PLUS auto links built from the phone number */}
      <InfoBlock title={t("cd.socials")}>
        {/* auto rows: every messaging app that takes a number, generated from
            this contact's primary number (skipped when already saved) */}
        {primary &&
          NUMBER_SOCIAL_APPS.filter((app) => !socials.some((s) => s.app === app)).map((app) => {
            const url = socialLinkFromNumber(app, primary, countryCode);
            if (!url) return null;
            const label = SOCIAL_APPS.find((a) => a.key === app)?.label ?? app;
            return (
              <View key={`auto-${app}`} style={cdStyles.infoRow}>
                <Pressable
                  style={{ flex: 1, minWidth: 0 }}
                  onPress={() => openWithFeedback(url, t("act.couldntOpen"))}
                  accessibilityLabel={`${label} — ${t("ce.fromNumber")}`}
                >
                  <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                    <View style={[cdStyles.socialIcon, { backgroundColor: C.brandSoft }]}>
                      <Icon name={socialIcon(app)} size={15} color={C.brand} />
                    </View>
                    <View style={{ flex: 1, minWidth: 0 }}>
                      <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }} numberOfLines={1}>
                        {label} · {prettyPhone(primary, countryCode)}
                      </Text>
                      <Text style={{ fontSize: F.tiny, color: C.faint, textTransform: "uppercase", letterSpacing: 1 }}>
                        {t("ce.fromNumber")}
                      </Text>
                    </View>
                    <Icon name="chevron-right" size={14} color={C.faint} />
                  </View>
                </Pressable>
                <Pressable
                  onPress={() => openWithFeedback(url, t("act.couldntOpen"))}
                  hitSlop={6}
                  style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}
                  accessibilityLabel={label}
                >
                  <Icon name="open-in-new" size={15} color={C.brand} />
                </Pressable>
              </View>
            );
          })}
        {/* saved socials — as many as the user wants */}
        {socials.map((s) => {
          const url = socialUrl(s);
          return (
            <View key={s.id} style={cdStyles.infoRow}>
              <Pressable
                style={{ flex: 1, minWidth: 0 }}
                onPress={() => {
                  if (url) openWithFeedback(url, t("act.couldntOpen"));
                  else
                    copyText(s.handle).then((ok) =>
                      toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error")
                    );
                }}
              >
                <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                  <View style={[cdStyles.socialIcon, { backgroundColor: C.brandSoft }]}>
                    <Icon name={socialIcon(s.app)} size={15} color={C.brand} />
                  </View>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }} numberOfLines={1}>
                      {s.handle}
                    </Text>
                    <Text style={{ fontSize: F.tiny, color: C.faint, textTransform: "uppercase", letterSpacing: 1 }}>
                      {s.app}
                    </Text>
                  </View>
                  <Icon name="chevron-right" size={14} color={C.faint} />
                </View>
              </Pressable>
              <Pressable
                onPress={() =>
                  copyText(s.handle).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
                }
                hitSlop={6}
                style={[cdStyles.miniBtn, { backgroundColor: C.brandSoft }]}
                accessibilityLabel={`${t("act.copy")} ${s.app}`}
              >
                <Icon name="content-copy" size={15} color={C.brand} />
              </Pressable>
              <Pressable
                onPress={() => onRemoveSocial(s.id)}
                hitSlop={6}
                style={[cdStyles.miniBtn, { backgroundColor: "rgba(214,69,69,0.12)" }]}
                accessibilityLabel={t("ce.removeSocial")}
              >
                <Icon name="trash-can-outline" size={15} color={C.red} />
              </Pressable>
            </View>
          );
        })}
        <Pressable
          onPress={() => onAddSocial("telegram")}
          style={[cdStyles.addSocialBtn, { borderColor: C.line, backgroundColor: C.cardAlt }]}
          accessibilityLabel={t("ce.addSocial")}
        >
          <Icon name="plus" size={15} color={C.brand} />
          <Text style={{ fontSize: F.small, color: C.brand, fontWeight: "700" }}>{t("ce.addSocial")}</Text>
        </Pressable>
      </InfoBlock>

      {/* addresses */}
      {!!contact.addresses.length && (
        <InfoBlock title={t("cd.addresses")}>
          {contact.addresses.map((a) => (
            <Pressable
              key={a.id}
              onPress={() =>
                copyText(a.formatted).then((ok) => toast(ok ? t("act.copied") : t("act.couldntCopy"), ok ? "ok" : "error"))
              }
              style={{ marginBottom: 6 }}
            >
              <Text style={{ fontSize: F.small, color: C.ink }}>
                {a.formatted} ({a.label})
              </Text>
            </Pressable>
          ))}
        </InfoBlock>
      )}

      {/* work */}
      {!!(contact.company || contact.jobTitle) && (
        <InfoBlock title="Work">
          <Text style={{ fontSize: F.body, color: C.ink, fontWeight: "600" }}>
            {contact.company || "—"}
          </Text>
          {!!contact.jobTitle && <Text style={{ fontSize: F.tiny, color: C.sub }}>{contact.jobTitle}</Text>}
        </InfoBlock>
      )}

      {/* notes */}
      {!!contact.note && (
        <InfoBlock title={t("cd.notesSection")}>
          <Text style={{ fontSize: F.small, color: C.sub, lineHeight: 20 }}>{contact.note}</Text>
        </InfoBlock>
      )}

      <Btn
        small
        label={t("act.edit")}
        icon="pencil-outline"
        variant="soft"
        onPress={onEdit}
        style={{ alignSelf: "flex-start", marginTop: 4 }}
      />
    </View>
  );
};

const Muted: React.FC<{ text: string }> = ({ text }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return <Text style={{ fontSize: F.small, color: C.faint }}>{text}</Text>;
};

const InfoBlock: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => {
  const C = useThemeColors(useApp((s) => s.settings.theme));
  return (
    <Card style={{ marginBottom: 10 }}>
      <Text style={{ fontSize: F.small, fontWeight: "800", color: C.brand, marginBottom: 6 }}>{title}</Text>
      {children}
    </Card>
  );
};

const cdStyles = StyleSheet.create({
  balanceLine: { flexDirection: "row", alignItems: "center", justifyContent: "center", flexWrap: "wrap", gap: 7, paddingBottom: 12, paddingTop: 2, minWidth: 0 },
  balancePill: {
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 4,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    minWidth: 0,
    maxWidth: "100%",
    flexShrink: 1,
  },
  tile: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 3,
    borderRadius: 14,
    paddingVertical: 6,
    minHeight: 52,
    minWidth: 0,
  },
  menuItem: { flexDirection: "row", alignItems: "center", gap: 14, paddingHorizontal: 10, paddingVertical: 13, borderRadius: 12, minHeight: 52 },
  infoRow: { flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 8 },
  miniBtn: { width: 34, height: 34, borderRadius: 17, alignItems: "center", justifyContent: "center" },
  labelChip: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3 },
  socialIcon: { width: 34, height: 34, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  historyRow: { flexDirection: "row", alignItems: "center", gap: 8, paddingVertical: 3, minHeight: 30 },
  /* v1.6.1 — unified chooser sheets (ChooserRow / ChooserAddRow) */
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    minHeight: 58,
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
    marginTop: 2,
    minHeight: 44,
  },
  addSocialBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: "dashed",
    paddingVertical: 10,
    marginTop: 2,
    minHeight: 44,
  },
});
