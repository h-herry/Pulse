"use client";

import * as React from "react";
import { PageSubtitle, PageTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * 页面容器
 * 统一页面留白与标题排版，保证全站节奏一致（苹果风的关键：大面积留白 + 克制标题层级）。
 */
export function PageShell({
  title,
  subtitle,
  actions,
  children,
  className,
  /** 看板等需要横向铺满的页面关闭最大宽度限制 */
  wide = false,
}: {
  title?: string;
  subtitle?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  wide?: boolean;
}) {
  return (
    <div className={cn("px-5 py-6 lg:px-8 lg:py-8", className)}>
      {title ? (
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div className="min-w-0">
            <PageTitle>{title}</PageTitle>
            {subtitle ? <PageSubtitle>{subtitle}</PageSubtitle> : null}
          </div>
          {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
        </div>
      ) : null}
      {wide ? children : <div className="max-w-[1200px]">{children}</div>}
    </div>
  );
}

/** 内容分区（卡片式分组，替代原来的多层折叠面板） */
export function Section({
  title,
  description,
  actions,
  children,
  className,
}: {
  title?: string;
  description?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={cn("mb-8", className)}>
      {title ? (
        <div className="mb-3 flex items-end justify-between gap-3">
          <div>
            <h2 className="text-[17px] font-semibold tracking-[-0.02em] text-fg">
              {title}
            </h2>
            {description ? (
              <p className="mt-0.5 text-[13px] text-fg-secondary">{description}</p>
            ) : null}
          </div>
          {actions}
        </div>
      ) : null}
      {children}
    </section>
  );
}
