"use client";

import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { ReactNode } from "react";
import { useApp } from "@/lib/store";
import { avatarColors, displayName, initialsOf, money as fmtMoney } from "@/lib/utils";
import type { Contact } from "@/lib/types";

export const cx = (...inputs: ClassValue[]) => twMerge(clsx(inputs));

/* ---------------- Avatar ---------------- */

const AV_SIZES = {
  xs: "h-8 w-8 rounded-[11px] text-[10px]",
  sm: "h-9 w-9 rounded-[13px] text-[11px]",
  md: "h-12 w-12 rounded-[17px] text-[13px]",
  lg: "h-14 w-14 rounded-[20px] text-[15px]",
  xl: "h-[72px] w-[72px] rounded-[26px] text-xl",
};

export function Avatar({
  contact,
  name,
  size = "md",
  className,
}: {
  contact?: Contact | null;
  name?: string;
  size?: keyof typeof AV_SIZES;
  className?: string;
}) {
  const photoId = contact?.photoId;
  const meta = useApp((s) => s.attachments.find((a) => a.id === photoId));
  const label = contact ? displayName(contact) : name || "?";
  const colors = avatarColors(contact);
  return (
    <div
      className={cx(
        "avatar relative flex shrink-0 items-center justify-center font-semibold overflow-hidden",
        AV_SIZES[size],
        className
      )}
      style={{ background: colors.color, color: colors.text }}
      aria-label={`Avatar for ${label}`}
    >
      {meta?.thumb ? (
        <img src={meta.thumb} alt={label} className="absolute inset-0 h-full w-full object-cover" />
      ) : (
        <span className="tracking-wide">{contact ? initialsOf(contact) : (name || "?").slice(0, 2).toUpperCase()}</span>
      )}
    </div>
  );
}

/* ---------------- Money ---------------- */

export function Money({
  value,
  signed = false,
  currency: override,
  className,
}: {
  value: number;
  signed?: boolean;
  currency?: string;
  className?: string;
}) {
  const settings = useApp((s) => s.settings);
  const hidden = settings.hideBalances;
  const cur = override || settings.currency;
  return (
    <span
      className={cx(
        "tabular-nums",
        hidden && "hidden-money",
        value > 0 && signed && "text-pos",
        value < 0 && signed && "text-neg",
        className
      )}
      title={hidden ? "Hidden — turn off in Settings" : undefined}
    >
      {fmtMoney(value, cur, { signed })}
    </span>
  );
}

/* ---------------- Buttons / chips ---------------- */

export function IconButton({
  children,
  onClick,
  label,
  className,
  variant = "ghost",
  disabled,
}: {
  children: ReactNode;
  onClick?: (e: React.MouseEvent) => void;
  label: string;
  className?: string;
  variant?: "ghost" | "brand" | "surface" | "danger";
  disabled?: boolean;
}) {
  const styles = {
    ghost: "border border-line2 bg-surface text-[#62716e] hover:bg-surface2 dark:text-mute",
    brand: "bg-brand text-white shadow-floating hover:bg-brandink",
    surface: "bg-brandsoft text-brand hover:bg-brandsoft2",
    danger: "bg-negsoft text-neg hover:bg-neg/15",
  }[variant];
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cx(
        "flex h-10 w-10 items-center justify-center rounded-full transition active:scale-95 disabled:opacity-40",
        styles,
        className
      )}
    >
      {children}
    </button>
  );
}

export function Chip({
  children,
  active,
  onClick,
  className,
}: {
  children: ReactNode;
  active?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cx(
        "shrink-0 rounded-full px-3 py-1.5 text-[11px] font-semibold transition active:scale-95",
        active
          ? "bg-brand text-white shadow-key"
          : "border border-line2 bg-surface text-mute hover:text-ink",
        className
      )}
    >
      {children}
    </button>
  );
}

export function Button({
  children,
  onClick,
  variant = "brand",
  size = "md",
  className,
  disabled,
  type = "button",
  autoFocus,
  ...rest
}: {
  children: ReactNode;
  onClick?: (e: React.MouseEvent) => void;
  variant?: "brand" | "soft" | "surface" | "danger" | "ghost";
  size?: "sm" | "md" | "lg";
  className?: string;
  disabled?: boolean;
  type?: "button" | "submit";
  autoFocus?: boolean;
} & Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "onClick" | "className" | "children" | "type">) {
  const styles = {
    brand: "bg-brand text-white shadow-floating hover:bg-brandink",
    soft: "bg-brandsoft text-brand hover:bg-brandsoft2",
    surface: "border border-line2 bg-surface text-ink shadow-key hover:bg-surface2",
    danger: "bg-neg text-white hover:bg-neg/90",
    ghost: "text-mute hover:text-ink",
  }[variant];
  const sizes = {
    sm: "px-3 py-2 text-[11px] rounded-xl",
    md: "px-4 py-2.5 text-xs rounded-xl",
    lg: "px-5 py-3.5 text-sm rounded-2xl",
  }[size];
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      autoFocus={autoFocus}
      {...rest}
      className={cx(
        "inline-flex items-center justify-center gap-2 font-semibold transition active:scale-[0.97] disabled:opacity-40 disabled:active:scale-100",
        styles,
        sizes,
        className
      )}
    >
      {children}
    </button>
  );
}

/* ---------------- Cards / layout ---------------- */

export function SectionCard({
  children,
  className,
  onClick,
}: {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
}) {
  return (
    <div
      onClick={onClick}
      className={cx(
        "rounded-[22px] border border-line bg-surface p-4 shadow-key",
        onClick && "cursor-pointer transition hover:bg-surface2 active:scale-[0.99]",
        className
      )}
    >
      {children}
    </div>
  );
}

export function SectionLabel({ children, right }: { children: ReactNode; right?: ReactNode }) {
  return (
    <div className="mb-3 mt-1 flex items-center justify-between">
      <p className="text-[11px] font-bold uppercase tracking-[1.7px] text-mute">{children}</p>
      {right}
    </div>
  );
}

export function EmptyState({
  icon,
  title,
  hint,
  action,
}: {
  icon?: ReactNode;
  title: string;
  hint?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-[22px] border border-line bg-surface px-6 py-10 text-center shadow-key">
      {icon && (
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-[20px] bg-brandsoft text-brand">
          {icon}
        </div>
      )}
      <p className="text-sm font-semibold text-ink">{title}</p>
      {hint && <p className="mt-1.5 text-xs leading-5 text-mute">{hint}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
  className,
}: {
  options: { value: T; label: ReactNode; badge?: number }[];
  value: T;
  onChange: (v: T) => void;
  className?: string;
}) {
  return (
    <div className={cx("app-scroll flex gap-1 overflow-x-auto rounded-2xl bg-surface2 p-1", className)}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => onChange(o.value)}
          className={cx(
            "relative flex-1 whitespace-nowrap rounded-xl px-3 py-2 text-[11px] font-semibold transition",
            value === o.value ? "bg-surface text-ink shadow-key" : "text-mute"
          )}
        >
          {o.label}
          {!!o.badge && (
            <span className="ml-1.5 inline-flex h-4 min-w-4 items-center justify-center rounded-full bg-neg px-1 text-[9px] font-bold text-white">
              {o.badge > 9 ? "9+" : o.badge}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}

export function ProgressBar({ value, className }: { value: number; className?: string }) {
  const pct = Math.max(0, Math.min(100, value));
  return (
    <div className={cx("h-1.5 w-full overflow-hidden rounded-full bg-line", className)}>
      <div
        className="h-full rounded-full bg-brand transition-all duration-300"
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}

/* ---------------- Form fields ---------------- */

export function Field({
  label,
  children,
  hint,
  className,
}: {
  label?: string;
  children: ReactNode;
  hint?: string;
  className?: string;
}) {
  return (
    <label className={cx("block", className)}>
      {label && (
        <span className="mb-1.5 block text-[10px] font-bold uppercase tracking-[1.5px] text-mute">
          {label}
        </span>
      )}
      {children}
      {hint && <span className="mt-1 block text-[10px] text-mute">{hint}</span>}
    </label>
  );
}

export const inputCls =
  "w-full rounded-2xl border border-line bg-surface px-4 py-3 text-sm text-ink placeholder:text-mute/70 transition focus:border-brand/50 focus:ring-2 focus:ring-brand/15";

export function SearchInput({
  value,
  onChange,
  placeholder,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  autoFocus?: boolean;
}) {
  return (
    <div className="flex h-12 items-center gap-3 rounded-2xl border border-line bg-surface px-4 shadow-key">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="var(--app-mute)" strokeWidth="2" strokeLinecap="round" aria-hidden>
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-4-4" />
      </svg>
      <input
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full bg-transparent text-sm text-ink placeholder:text-mute/70"
        placeholder={placeholder || "Search…"}
        aria-label={placeholder || "Search"}
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange("")}
          aria-label="Clear search"
          className="flex h-6 w-6 items-center justify-center rounded-full bg-surface2 text-mute"
        >
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      )}
    </div>
  );
}

/* ---------------- balance pill ---------------- */

export function BalancePill({ value, size = "md" }: { value: number; size?: "sm" | "md" | "lg" }) {
  const settled = Math.abs(value) < 0.005;
  const sizes = { sm: "text-[10px] px-2 py-0.5", md: "text-[11px] px-2.5 py-1", lg: "text-sm px-3.5 py-1.5" }[size];
  return (
    <span
      className={cx(
        "inline-flex items-center rounded-full font-bold tabular-nums",
        settled ? "bg-surface2 text-mute" : value > 0 ? "bg-brandsoft text-pos" : "bg-negsoft text-neg",
        sizes
      )}
    >
      <Money value={value} signed={!settled} />
    </span>
  );
}
