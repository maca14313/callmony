"use client";

import { useMemo, useState } from "react";
import {
  Bookmark,
  Calculator,
  CircleDollarSign,
  Copy,
  Ellipsis,
  List,
  ListChecks,
  ListPlus,
  Plus,
  Repeat,
  SquareCheckBig,
  Trash2,
} from "lucide-react";
import { useApp } from "@/lib/store";
import { useUI } from "@/lib/ui-store";
import { listTotals } from "@/lib/calc";
import type { CalcList, ID, ListKind, OwnerId } from "@/lib/types";
import { SwipeRow } from "../kit/SwipeRow";
import { useConfirm } from "../kit/Confirm";
import { BottomSheet } from "../kit/Sheet";
import { toastOk, toastUndoLast } from "../kit/toast";
import {
  Button,
  EmptyState,
  Money,
  ProgressBar,
  SectionLabel,
  cx,
} from "../kit/primitives";

const KIND_META: Record<ListKind, { label: string; icon: typeof List; hint: string }> = {
  calculating: {
    label: "Calculating",
    icon: Calculator,
    hint: "Items with qty, price, payments & totals",
  },
  checklist: { label: "Checklist", icon: ListChecks, hint: "Tick things off — see done progress" },
  todo: { label: "To-do", icon: SquareCheckBig, hint: "Tasks with priority & due dates" },
  simple: { label: "Simple", icon: List, hint: "A plain list of things" },
};

const REPEAT_LABEL: Record<CalcList["repeat"], string> = {
  none: "No repeat",
  daily: "Daily",
  weekly: "Weekly",
  monthly: "Monthly",
  yearly: "Yearly",
};

function KindBadge({ kind }: { kind: ListKind }) {
  const meta = KIND_META[kind];
  const Icon = meta.icon;
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-brandsoft px-2.5 py-1 text-[10px] font-bold text-brand">
      <Icon size={11} aria-hidden />
      {meta.label}
    </span>
  );
}

/** Card for one active (non-template) list. */
function ListCard({ list, onMenu }: { list: CalcList; onMenu: (id: ID) => void }) {
  const ui = useUI();
  const isCalc = list.kind === "calculating";
  const totals = useMemo(() => listTotals(list), [list]);
  const done = list.items.filter((i) => i.checked).length;

  return (
    <div onContextMenu={(e) => { e.preventDefault(); onMenu(list.id); }}>
      <SwipeRow
        onTap={() => ui.open({ type: "list", listId: list.id })}
        onLongPress={() => onMenu(list.id)}
        className="rounded-[22px]"
      >
        <div className="rounded-[22px] border border-line bg-surface p-4 shadow-key">
          <div className="flex items-start gap-3">
            <span
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-brandsoft text-brand"
              aria-hidden
            >
              {(() => {
                const Icon = KIND_META[list.kind].icon;
                return <Icon size={19} />;
              })()}
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-[14px] font-bold tracking-tight text-ink">{list.title}</p>
              <p className="mt-0.5 text-[11px] text-mute">
                {list.items.length} item{list.items.length !== 1 ? "s" : ""}
                {list.kind === "checklist" && list.items.length > 0 ? ` · ${done} done` : ""}
                {list.repeat !== "none" ? ` · ${REPEAT_LABEL[list.repeat]}` : ""}
              </p>
            </div>
            <button
              type="button"
              aria-label={`Actions for ${list.title}`}
              onClick={(e) => {
                e.stopPropagation();
                onMenu(list.id);
              }}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-mute transition hover:bg-surface2 active:scale-90"
            >
              <Ellipsis size={16} />
            </button>
          </div>

          {isCalc && (
            <div className="mt-3">
              <div className="flex items-baseline justify-between gap-2 text-[11px]">
                <span className="text-mute">
                  Total <span className="text-[12px] font-bold text-ink"><Money value={totals.grand} currency={list.currency} /></span>
                </span>
                <span className={cx("text-[12px] font-bold", totals.pending > 0.005 ? "text-neg" : "text-pos")}>
                  {totals.pending > 0.005 ? (
                    <>
                      Pending <Money value={totals.pending} currency={list.currency} />
                    </>
                  ) : (
                    "All settled ✓"
                  )}
                </span>
              </div>
              <ProgressBar className="mt-1.5" value={totals.grand > 0.005 ? (totals.paid / totals.grand) * 100 : 0} />
              <p className="mt-1 text-[10.5px] text-mute">
                Paid <Money value={totals.paid} currency={list.currency} />
              </p>
            </div>
          )}

          <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
            <KindBadge kind={list.kind} />
            {list.isTemplate && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-surface2 px-2.5 py-1 text-[10px] font-bold text-mute">
                <Bookmark size={11} aria-hidden /> Template
              </span>
            )}
            {isCalc && list.postToLedger && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-amber/10 px-2.5 py-1 text-[10px] font-bold text-amber">
                <CircleDollarSign size={11} aria-hidden /> counts in balance
              </span>
            )}
            {list.repeat !== "none" && (
              <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-surface2 px-2.5 py-1 text-[10px] font-bold text-mute">
                <Repeat size={11} aria-hidden /> {REPEAT_LABEL[list.repeat]}
              </span>
            )}
          </div>
        </div>
      </SwipeRow>
    </div>
  );
}

export function ListsSection({ ownerId }: { ownerId: OwnerId }) {
  const ui = useUI();
  const confirm = useConfirm();
  const lists = useApp((s) => s.lists);
  const duplicateList = useApp((s) => s.duplicateList);
  const deleteList = useApp((s) => s.deleteList);

  const [kindOpen, setKindOpen] = useState(false);
  const [menuId, setMenuId] = useState<ID | null>(null);

  const active = useMemo(
    () =>
      lists
        .filter((l) => l.ownerId === ownerId && !l.deletedAt && !l.isTemplate)
        .sort((a, b) => b.updatedAt - a.updatedAt),
    [lists, ownerId]
  );
  const templates = useMemo(
    () =>
      lists
        .filter((l) => l.ownerId === ownerId && !l.deletedAt && l.isTemplate)
        .sort((a, b) => b.updatedAt - a.updatedAt),
    [lists, ownerId]
  );

  const menuList = menuId ? lists.find((l) => l.id === menuId && !l.deletedAt) : undefined;

  const onDuplicate = (id: ID, fresh = false) => {
    const nid = duplicateList(id, fresh ? { fresh: true } : undefined);
    if (nid) {
      ui.open({ type: "list", listId: nid });
      toastOk("List duplicated");
    }
  };

  const onUseTemplate = (id: ID) => {
    const nid = duplicateList(id, { fresh: true });
    if (nid) {
      ui.open({ type: "list", listId: nid });
      toastOk("New list from template");
    }
  };

  const onDelete = async (list: CalcList) => {
    const ok = await confirm({
      title: `Delete “${list.title}”?`,
      message: "The list and its items move to Trash — you can undo from the toast.",
      confirmLabel: "Delete",
      tone: "danger",
    });
    if (!ok) return;
    setMenuId(null);
    deleteList(list.id);
    toastUndoLast();
  };

  return (
    <section aria-label="Lists">
      <SectionLabel
        right={
          <Button variant="soft" size="sm" onClick={() => setKindOpen(true)}>
            <Plus size={13} /> New
          </Button>
        }
      >
        Lists
      </SectionLabel>

      <div className="space-y-2.5">
        {active.map((l) => (
          <ListCard key={l.id} list={l} onMenu={setMenuId} />
        ))}

        {!active.length && (
          <EmptyState
            icon={<ListPlus size={22} />}
            title="No lists yet"
            hint="Calculating lists track items, prices and payments. Templates make repeat bills one tap."
            action={
              <Button variant="soft" onClick={() => setKindOpen(true)}>
                <Plus size={15} /> New list
              </Button>
            }
          />
        )}
      </div>

      {/* templates */}
      {templates.length > 0 && (
        <div className="mt-6">
          <SectionLabel>Templates · for repeat bills</SectionLabel>
          <div className="space-y-2">
            {templates.map((t) => (
              <div
                key={t.id}
                className="flex items-center gap-3 rounded-[22px] border border-dashed border-line bg-surface2 p-3.5"
              >
                <span
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[14px] bg-surface text-brand shadow-key"
                  aria-hidden
                >
                  <Bookmark size={16} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13px] font-bold text-ink">{t.title.replace(/ \(template\)$/, "")}</p>
                  <p className="mt-0.5 truncate text-[10.5px] text-mute">
                    {KIND_META[t.kind].label} · {t.items.length} item{t.items.length !== 1 ? "s" : ""}
                    {t.repeat !== "none" ? ` · ${REPEAT_LABEL[t.repeat]}` : ""}
                  </p>
                </div>
                <Button variant="soft" size="sm" className="shrink-0" onClick={() => onUseTemplate(t.id)}>
                  <Copy size={13} /> Use
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* kind picker */}
      <BottomSheet open={kindOpen} onClose={() => setKindOpen(false)} title="New list">
        <div className="space-y-2">
          {(Object.keys(KIND_META) as ListKind[]).map((k) => {
            const meta = KIND_META[k];
            const Icon = meta.icon;
            return (
              <button
                key={k}
                type="button"
                onClick={() => {
                  setKindOpen(false);
                  ui.open({ type: "listEdit", ownerId, kind: k });
                }}
                className="flex w-full items-center gap-3 rounded-2xl border border-line bg-surface p-3.5 text-left shadow-key transition hover:bg-surface2 active:scale-[0.98]"
              >
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[15px] bg-brandsoft text-brand" aria-hidden>
                  <Icon size={19} />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-[13px] font-bold text-ink">{meta.label}</span>
                  <span className="block text-[11px] leading-4 text-mute">{meta.hint}</span>
                </span>
              </button>
            );
          })}
        </div>
      </BottomSheet>

      {/* long-press menu */}
      <BottomSheet
        open={!!menuList}
        onClose={() => setMenuId(null)}
        title={menuList?.title || "List"}
      >
        {menuList && (
          <div className="space-y-1">
            <button
              type="button"
              onClick={() => {
                const id = menuList.id;
                setMenuId(null);
                ui.open({ type: "list", listId: id });
              }}
              className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
            >
              <List size={17} className="text-brand" aria-hidden />
              <span className="text-[13px] font-semibold text-ink">Open</span>
            </button>
            <button
              type="button"
              onClick={() => {
                const id = menuList.id;
                setMenuId(null);
                onDuplicate(id);
              }}
              className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
            >
              <Copy size={17} className="text-brand" aria-hidden />
              <span className="text-[13px] font-semibold text-ink">Duplicate</span>
            </button>
            <button
              type="button"
              onClick={() => {
                duplicateList(menuList.id, { asTemplate: true });
                setMenuId(null);
                toastOk("Saved as template");
              }}
              className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left transition hover:bg-surface2 active:scale-[0.98]"
            >
              <Bookmark size={17} className="text-brand" aria-hidden />
              <span className="text-[13px] font-semibold text-ink">Save as template</span>
            </button>
            <button
              type="button"
              onClick={() => void onDelete(menuList)}
              className="flex w-full items-center gap-3 rounded-2xl px-3 py-3 text-left text-neg transition hover:bg-negsoft/50 active:scale-[0.98]"
            >
              <Trash2 size={17} className="text-neg" aria-hidden />
              <span className="text-[13px] font-semibold text-neg">Delete</span>
            </button>
          </div>
        )}
      </BottomSheet>
    </section>
  );
}
