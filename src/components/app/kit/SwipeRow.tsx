"use client";

import { ReactNode, useRef, useState } from "react";
import { motion, useMotionValue, useAnimation } from "framer-motion";
import { cx } from "./primitives";
import { vibrate } from "@/lib/utils";

export interface SwipeAction {
  key: string;
  icon: ReactNode;
  label?: string;
  className: string; // bg + text color classes
  onClick: () => void;
}

/**
 * A row that supports:
 *  - tap → onTap
 *  - long-press (500ms) → onLongPress
 *  - swipe left/right to reveal action buttons
 * Content is user-supplied; actions sit behind the content.
 */
export function SwipeRow({
  children,
  left = [],
  right = [],
  onTap,
  onLongPress,
  className,
  actionWidth = 56,
}: {
  children: ReactNode;
  left?: SwipeAction[];
  right?: SwipeAction[];
  onTap?: () => void;
  onLongPress?: () => void;
  className?: string;
  actionWidth?: number;
}) {
  const x = useMotionValue(0);
  const controls = useAnimation();
  const [openSide, setOpenSide] = useState<"left" | "right" | null>(null);
  const movedRef = useRef(false);
  const longPressRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const consumedRef = useRef(false);
  const startXRef = useRef(0);

  const leftW = left.length * actionWidth;
  const rightW = right.length * actionWidth;

  const snapTo = (side: "left" | "right" | null) => {
    setOpenSide(side);
    controls.start({
      x: side === "left" ? leftW : side === "right" ? -rightW : 0,
      transition: { type: "spring", damping: 26, stiffness: 340 },
    });
  };

  const clearLongPress = () => {
    if (longPressRef.current) {
      clearTimeout(longPressRef.current);
      longPressRef.current = null;
    }
  };

  const handleTap = () => {
    if (consumedRef.current) {
      consumedRef.current = false;
      return;
    }
    if (openSide) {
      snapTo(null);
      return;
    }
    onTap?.();
  };

  const runAction = (a: SwipeAction) => {
    a.onClick();
    snapTo(null);
  };

  return (
    <div className={cx("relative select-none overflow-hidden rounded-2xl", className)}>
      {/* left actions (revealed by swiping right) */}
      {left.length > 0 && (
        <div className="absolute inset-y-0 left-0 flex items-stretch">
          {left.map((a) => (
            <button
              key={a.key}
              type="button"
              aria-label={a.label || a.key}
              onClick={() => runAction(a)}
              className={cx("flex w-14 items-center justify-center text-white transition active:scale-95", a.className)}
            >
              {a.icon}
            </button>
          ))}
        </div>
      )}
      {/* right actions (revealed by swiping left) */}
      {right.length > 0 && (
        <div className="absolute inset-y-0 right-0 flex items-stretch">
          {right.map((a) => (
            <button
              key={a.key}
              type="button"
              aria-label={a.label || a.key}
              onClick={() => runAction(a)}
              className={cx("flex w-14 items-center justify-center text-white transition active:scale-95", a.className)}
            >
              {a.icon}
            </button>
          ))}
        </div>
      )}

      <motion.div
        drag={left.length || right.length ? "x" : false}
        style={{ x }}
        animate={controls}
        dragConstraints={{ left: -rightW, right: leftW }}
        dragElastic={0.08}
        dragDirectionLock
        onPointerDown={(e) => {
          movedRef.current = false;
          consumedRef.current = false;
          startXRef.current = e.clientX;
          if (onLongPress) {
            longPressRef.current = setTimeout(() => {
              consumedRef.current = true;
              vibrate(15);
              onLongPress();
            }, 500);
          }
        }}
        onPointerMove={(e) => {
          if (Math.abs(e.clientX - startXRef.current) > 8) {
            movedRef.current = true;
            clearLongPress();
          }
        }}
        onPointerUp={clearLongPress}
        onPointerCancel={clearLongPress}
        onDragEnd={(_, info) => {
          clearLongPress();
          if (movedRef.current) consumedRef.current = true;
          const current = x.get();
          const pastHalf = Math.abs(current) > (leftW || rightW) / 2;
          const flung = Math.abs(info.velocity.x) > 380;
          if (current < 0 && (pastHalf || (flung && info.velocity.x < 0)) && rightW > 0) {
            vibrate(8);
            snapTo("right");
          } else if (current > 0 && (pastHalf || (flung && info.velocity.x > 0)) && leftW > 0) {
            vibrate(8);
            snapTo("left");
          } else {
            snapTo(null);
          }
        }}
        onClick={handleTap}
        className="relative z-[1] cursor-pointer bg-canvas"
      >
        {children}
      </motion.div>
    </div>
  );
}
