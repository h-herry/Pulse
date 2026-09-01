"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/** 卡片容器：白面 + 发丝边 + 柔和阴影（hover 可选微抬升） */
export function Card({
  className,
  interactive = false,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { interactive?: boolean }) {
  return (
    <div
      className={cn(
        "rounded-[var(--radius-md)] border border-hairline bg-surface",
        "shadow-xs transition-all duration-200 easing-apple",
        interactive &&
          "cursor-pointer hover:shadow-md hover:border-line-strong hover:-translate-y-px",
        className
      )}
      {...props}
    />
  );
}

export function CardHeader({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 px-5 pt-4 pb-3",
        className
      )}
      {...props}
    />
  );
}

export function CardTitle({
  className,
  ...props
}: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn("text-[15px] font-semibold text-fg tracking-[-0.02em]", className)}
      {...props}
    />
  );
}

export function CardContent({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-5 pb-5", className)} {...props} />;
}

/** 页面大标题 */
export function PageTitle({
  className,
  ...props
}: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h1
      className={cn("text-[28px] font-semibold text-fg tracking-[-0.025em] leading-tight", className)}
      {...props}
    />
  );
}

export function PageSubtitle({
  className,
  ...props
}: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("text-[14px] text-fg-secondary mt-1", className)} {...props} />;
}
