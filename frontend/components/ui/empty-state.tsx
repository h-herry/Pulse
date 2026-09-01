"use client";

import * as React from "react";
import { AlertTriangle, Inbox, SearchX } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "./button";

/**
 * 统一三态组件：空态 / 错误态
 * 生产级要求——任何列表页都必须覆盖 loading / empty / error 三种状态。
 */

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
  compact = false,
}: {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
  compact?: boolean;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center text-center",
        compact ? "py-8" : "py-16",
        className
      )}
    >
      <div
        className={cn(
          "mb-4 grid place-items-center rounded-full bg-surface-3 text-fg-muted",
          compact ? "size-10" : "size-14"
        )}
      >
        {icon ?? <Inbox className={compact ? "size-5" : "size-6"} />}
      </div>
      <p
        className={cn(
          "font-semibold text-fg tracking-[-0.01em]",
          compact ? "text-[14px]" : "text-[16px]"
        )}
      >
        {title}
      </p>
      {description ? (
        <p className="mt-1.5 max-w-sm text-[13px] leading-relaxed text-fg-secondary">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

/** 搜索无结果 */
export function NoResults({ keyword }: { keyword?: string }) {
  return (
    <EmptyState
      icon={<SearchX className="size-6" />}
      title="没有匹配的结果"
      description={keyword ? `没有找到与「${keyword}」相关的内容` : "换个筛选条件试试"}
    />
  );
}

/** 错误态：可重试 */
export function ErrorState({
  title = "加载失败",
  message,
  onRetry,
  className,
}: {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <EmptyState
      className={className}
      icon={<AlertTriangle className="size-6 text-warning" />}
      title={title}
      description={message ?? "请检查网络或稍后重试"}
      action={
        onRetry ? (
          <Button variant="secondary" size="sm" onClick={onRetry}>
            重试
          </Button>
        ) : null
      }
    />
  );
}

/** 未选择上下文时的引导（团队/项目未选） */
export function ContextRequired({
  what = "项目",
  action,
}: {
  what?: string;
  action?: React.ReactNode;
}) {
  return (
    <EmptyState
      title={`请先选择${what}`}
      description={`使用顶部切换器选择${what}，即可查看这里的内容`}
      action={action}
    />
  );
}
