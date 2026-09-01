"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/** 骨架屏（加载态）：微光渐变 */
export function Skeleton({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("shimmer rounded-[var(--radius-sm)]", className)}
      aria-hidden
      {...props}
    />
  );
}

/** 列表骨架：N 行 */
export function SkeletonList({ rows = 5, className }: { rows?: number; className?: string }) {
  return (
    <div className={cn("space-y-2", className)}>
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

/** 看板骨架：N 列 × M 卡 */
export function SkeletonBoard({
  columns = 4,
  cards = 3,
}: {
  columns?: number;
  cards?: number;
}) {
  return (
    <div className="flex gap-4 overflow-hidden">
      {Array.from({ length: columns }).map((_, c) => (
        <div key={c} className="flex w-[280px] shrink-0 flex-col gap-3">
          <Skeleton className="h-7 w-32" />
          <div className="rounded-[var(--radius-md)] bg-surface-2 p-2 space-y-2">
            {Array.from({ length: cards }).map((_, r) => (
              <Skeleton key={r} className="h-20 w-full" />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
