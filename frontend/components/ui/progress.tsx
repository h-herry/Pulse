"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/** 细进度条（6px，圆角，品牌色填充） */
export function Progress({
  value = 0,
  max = 100,
  className,
  tone = "accent",
  showLabel = false,
}: {
  value?: number;
  max?: number;
  className?: string;
  tone?: "accent" | "done" | "warning" | "danger";
  showLabel?: boolean;
}) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  const toneClass = {
    accent: "bg-accent",
    done: "bg-done",
    warning: "bg-warning",
    danger: "bg-danger",
  }[tone];

  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-surface-3"
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className={cn("h-full rounded-full transition-[width] duration-500 easing-apple", toneClass)}
          style={{ width: `${pct}%` }}
        />
      </div>
      {showLabel ? (
        <span className="w-9 shrink-0 text-right text-[12px] tabular-nums text-fg-secondary">
          {Math.round(pct)}%
        </span>
      ) : null}
    </div>
  );
}
