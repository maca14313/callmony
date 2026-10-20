"use client";

import { useMemo } from "react";
import { Check, Mail, MessageCircle, PhoneCall, Send, Star } from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI, type ContactAction } from "@/lib/ui-store";
import type { Contact, ID } from "@/lib/types";
import { prettyPhone } from "@/lib/utils";
import { mailtoLink, openExternal, smsLink, tgLink, waLink } from "@/lib/share";
import { useT } from "@/lib/i18n";
import { BottomSheet } from "./Sheet";
import { Avatar, cx } from "./primitives";
import { toastErr } from "./toast";

/* ------------------------------------------------------------------ */
/* Contact action picker — "before calling, which number?"             */
/* One shared host sheet; every call/SMS/WhatsApp/Telegram/Email      */
/* button anywhere in the app goes through act() below.               */
/* ------------------------------------------------------------------ */

export interface ActionTarget {
  id: string;
  label: string; // e.g. mobile · work · Telegram
  value: string; // raw value used by the link builders
  display: string; // pretty value shown in the sheet
  isDefault: boolean;
}

export function targetsFor(contact: Contact, action: ContactAction): ActionTarget[] {
  const cc = useApp.getState().settings.countryCode;
  switch (action) {
    case "call":
    case "sms":
    case "whatsapp":
      return contact.phones.map((p) => ({
        id: p.id,
        label: p.label,
        value: p.number,
        display: prettyPhone(p.number, cc),
        isDefault: !!p.primary || (!contact.phones.some((x) => x.primary) && contact.phones[0]?.id === p.id),
      }));
    case "telegram": {
      const socials: ActionTarget[] = contact.socials
        .filter((s) => s.app === "telegram")
        .map((s) => ({
          id: s.id,
          label: "Telegram",
          value: s.handle,
          display: s.handle.startsWith("@") ? s.handle : `@${s.handle}`,
          isDefault: false,
        }));
      const phones: ActionTarget[] = contact.phones.map((p) => ({
        id: `ph-${p.id}`,
        label: `Telegram · ${p.label}`,
        value: prettyPhone(p.number, cc),
        display: prettyPhone(p.number, cc),
        isDefault: false,
      }));
      const all = [...socials, ...phones];
      if (all.length) all[0].isDefault = true;
      return all;
    }
    case "email":
      return contact.emails.map((e) => ({
        id: e.id,
        label: e.label,
        value: e.address,
        display: e.address,
        isDefault: !!e.primary || (!contact.emails.some((x) => x.primary) && contact.emails[0]?.id === e.id),
      }));
  }
}

function runAction(contact: Contact, action: ContactAction, target: ActionTarget, text?: string) {
  const cc = useApp.getState().settings.countryCode;
  const app = useApp.getState();
  const ui = useUI.getState();
  switch (action) {
    case "call":
      ui.open({ type: "call", phone: target.value, contactId: contact.id });
      break;
    case "sms":
      openExternal(smsLink(target.value, text, cc));
      if (text) app.logMessage(contact.id, "sms", text);
      break;
    case "whatsapp":
      openExternal(waLink(target.value, text, cc));
      if (text) app.logMessage(contact.id, "whatsapp", text);
      break;
    case "telegram":
      openExternal(tgLink(target.value));
      if (text) app.logMessage(contact.id, "telegram", text);
      break;
    case "email":
      openExternal(mailtoLink(target.value));
      break;
  }
}

/**
 * useContactActions().act(contactOrId, action, { text }) —
 * • no target → error toast
 * • exactly one target → runs immediately
 * • several targets → opens the shared picker sheet (default marked)
 */
export function useContactActions() {
  return useMemo(
    () => ({
      act: (contactOrId: Contact | ID, action: ContactAction, opts?: { text?: string }) => {
        const id = typeof contactOrId === "string" ? contactOrId : contactOrId.id;
        const contact = useApp.getState().contacts.find((c) => c.id === id && !c.deletedAt);
        if (!contact) return;
        const targets = targetsFor(contact, action);
        if (targets.length === 0) {
          toastErr(
            action === "email"
              ? "No email address on this contact"
              : action === "telegram"
                ? "No Telegram on this contact"
                : "No phone number on this contact"
          );
          return;
        }
        if (targets.length === 1) {
          runAction(contact, action, targets[0], opts?.text);
          return;
        }
        useUI.getState().openAction({ contactId: contact.id, action, text: opts?.text });
      },
    }),
    []
  );
}

const ACTION_LABEL_KEY: Record<ContactAction, string> = {
  call: "act.call",
  sms: "act.sms",
  whatsapp: "act.whatsapp",
  telegram: "act.telegram",
  email: "act.email",
};

const ACTION_ICON: Record<ContactAction, typeof PhoneCall> = {
  call: PhoneCall,
  sms: MessageCircle,
  whatsapp: MessageCircle,
  telegram: Send,
  email: Mail,
};

/** The single hosted picker sheet — rendered once in the app shell. */
export function ActionPickerHost() {
  const picker = useUI((s) => s.actionPicker);
  const closeAction = useUI((s) => s.closeAction);
  const contact = useApp((s) =>
    picker ? s.contacts.find((c) => c.id === picker.contactId && !c.deletedAt) : undefined
  );
  const t = useT();

  const targets = useMemo(
    () => (contact && picker ? targetsFor(contact, picker.action) : []),
    [contact, picker]
  );

  if (!picker || !contact) return null;
  const Icon = ACTION_ICON[picker.action];

  const sorted = [...targets].sort((a, b) => Number(b.isDefault) - Number(a.isDefault));

  return (
    <BottomSheet
      open
      onClose={closeAction}
      title={
        <span className="flex items-center gap-2">
          <Icon size={16} className="text-brand" />
          {t(ACTION_LABEL_KEY[picker.action])}
          <span className="text-mute">·</span>
          <span className="truncate">{contact.firstName || contact.lastName || "Contact"}</span>
        </span>
      }
      zIndex={80}
    >
      <p className="px-1 pb-2 text-[11px] text-mute">{t("cd.chooseNumber")}</p>
      <div className="space-y-1.5">
        {sorted.map((target) => (
          <button
            key={target.id}
            onClick={() => {
              runAction(contact, picker.action, target, picker.text);
              closeAction();
            }}
            className={cx(
              "flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left transition active:scale-[0.98]",
              target.isDefault ? "border-brand/40 bg-brandsoft" : "border-line bg-surface"
            )}
          >
            <Avatar contact={contact} size="xs" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[14px] font-semibold text-ink">{target.display}</span>
              <span className="block text-[10px] uppercase tracking-[1.5px] text-mute">{target.label}</span>
            </span>
            {target.isDefault ? (
              <span className="flex shrink-0 items-center gap-1 rounded-full bg-brand px-2 py-1 text-[9px] font-bold text-white">
                <Star size={10} fill="currentColor" />
                {t("cd.default")}
              </span>
            ) : (
              <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-line2">
                <Check size={12} className="text-transparent" />
              </span>
            )}
          </button>
        ))}
      </div>
    </BottomSheet>
  );
}
