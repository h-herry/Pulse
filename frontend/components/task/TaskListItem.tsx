"use client";

import * as React from "react";
import { CalendarDays, Flame } from "lucide-react";
import { PriorityDot, StatusBadge } from "@/components/ui/badge";
import { UserAvatar } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { useNow } from "@/lib/use-now";
import type { BoardTask } from "@/lib/types";

/**
 * 任务行
 * 列表页（我的任务 / 总览 / 迭代）共用，点击即从右侧抽屉打开详情，
 * 不跳转页面——避免「进详情再返回」的往返。
 */
export function TaskListItem({
  task,
  onOpen,
  showProject = false,
  showAssignee = true,
}: {
  task: BoardTask;
  onOpen: (id: number) => void;
  showProject?: boolean;
  showAssignee?: boolean;
}) {
  // 逾期判断需要「当前时间」；经 useNow 订阅而来，避免在渲染期调用 Date.now() 造成非纯渲染
  const now = useNow();
  const overdue =
    task.dueDate != null && task.status !== "DONE" && Date.parse(task.dueDate) < now;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onOpen(task.id)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen(task.id);
        }
      }}
      className={cn(
        "flex cursor-pointer items-center gap-3 border-b border-hairline px-4 py-3",
        "transition-colors duration-150 hover:bg-surface-2",
        "focus-visible:outline-none focus-visible:bg-surface-2",
        "last:border-b-0"
      )}
      aria-label={`任务：${task.title}`}
    >
      <PriorityDot priority={task.priority} />

      <span className="min-w-0 flex-1 truncate text-[13.5px] font-medium text-fg">
        {task.title}
      </span>

      {showProject && task.projectKey ? (
        <span className="hidden shrink-0 text-[11.5px] uppercase tabular-nums text-fg-muted sm:inline">
          {task.projectKey}-{task.taskNumber}
        </span>
      ) : null}

      {task.storyPoints ? (
        <span className="hidden shrink-0 items-center gap-0.5 rounded bg-surface-3 px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-fg-secondary md:inline-flex">
          <Flame className="size-3" />
          {task.storyPoints}
        </span>
      ) : null}

      {task.dueDate ? (
        <span
          className={cn(
            "hidden shrink-0 items-center gap-1 text-[11.5px] tabular-nums lg:inline-flex",
            overdue ? "text-danger" : "text-fg-muted"
          )}
        >
          <CalendarDays className="size-3" />
          {task.dueDate}
        </span>
      ) : null}

      <StatusBadge status={task.status} size="sm" className="hidden sm:inline-flex" />

      {showAssignee ? <UserAvatar name={task.assigneeName} size={22} /> : null}
    </div>
  );
}
