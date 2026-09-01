"use client";

import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full font-medium leading-none whitespace-nowrap transition-colors",
  {
    variants: {
      tone: {
        neutral: "bg-surface-3 text-fg-secondary",
        accent: "bg-accent-soft text-accent",
        todo: "bg-todo-soft text-todo",
        doing: "bg-doing-soft text-doing",
        review: "bg-review-soft text-review",
        done: "bg-done-soft text-done",
        success: "bg-success-soft text-success",
        warning: "bg-warning-soft text-warning",
        danger: "bg-danger-soft text-danger",
        info: "bg-info-soft text-info",
      },
      size: {
        sm: "h-5 px-2 text-[11px]",
        md: "h-[22px] px-2.5 text-[12px]",
      },
    },
    defaultVariants: { tone: "neutral", size: "md" },
  }
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, size, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone, size }), className)} {...props} />;
}

/* ---------- 状态 ---------- */

export const STATUS_META: Record<
  string,
  { label: string; tone: NonNullable<BadgeProps["tone"]>; dot: string }
> = {
  TODO: { label: "待办", tone: "todo", dot: "bg-todo" },
  IN_PROGRESS: { label: "进行中", tone: "doing", dot: "bg-doing" },
  REVIEW: { label: "审查中", tone: "review", dot: "bg-review" },
  DONE: { label: "已完成", tone: "done", dot: "bg-done" },
};

export function StatusBadge({
  status,
  size = "md",
  className,
}: {
  status?: string | null;
  size?: "sm" | "md";
  className?: string;
}) {
  const meta = STATUS_META[status ?? ""] ?? STATUS_META.TODO;
  return (
    <Badge tone={meta.tone} size={size} className={className}>
      <span className={cn("size-1.5 rounded-full", meta.dot)} aria-hidden />
      {meta.label}
    </Badge>
  );
}

/* ---------- 优先级 ---------- */

export const PRIORITY_META: Record<
  string,
  { label: string; short: string; dot: string; soft: string; text: string }
> = {
  P0: { label: "P0 紧急", short: "P0", dot: "bg-p0", soft: "bg-p0-soft", text: "text-p0" },
  P1: { label: "P1 高", short: "P1", dot: "bg-p1", soft: "bg-p1-soft", text: "text-p1" },
  P2: { label: "P2 中", short: "P2", dot: "bg-p2", soft: "bg-p2-soft", text: "text-p2" },
  P3: { label: "P3 低", short: "P3", dot: "bg-p3", soft: "bg-p3-soft", text: "text-p3" },
  P4: { label: "P4 很低", short: "P4", dot: "bg-p4", soft: "bg-p4-soft", text: "text-p4" },
  P5: { label: "P5 最低", short: "P5", dot: "bg-p5", soft: "bg-p5-soft", text: "text-p5" },
};

export function PriorityBadge({
  priority,
  size = "md",
  className,
}: {
  priority?: string | null;
  size?: "sm" | "md";
  className?: string;
}) {
  const meta = PRIORITY_META[priority ?? ""] ?? PRIORITY_META.P2;
  return (
    <Badge size={size} className={cn(meta.soft, meta.text, className)}>
      <span className={cn("size-1.5 rounded-full", meta.dot)} aria-hidden />
      {meta.short}
    </Badge>
  );
}

export function PriorityDot({
  priority,
  className,
}: {
  priority?: string | null;
  className?: string;
}) {
  const meta = PRIORITY_META[priority ?? ""] ?? PRIORITY_META.P2;
  return (
    <span
      className={cn("size-2 rounded-full shrink-0", meta.dot, className)}
      title={meta.label}
      aria-label={meta.label}
    />
  );
}
