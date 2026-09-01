"use client";

import * as React from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { CalendarDays, Flame } from "lucide-react";
import { PriorityDot } from "@/components/ui/badge";
import { UserAvatar } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { useNow } from "@/lib/use-now";
import type { BoardTask } from "@/lib/types";

/** 看板卡片：白面 + 发丝边，拖拽时轻微抬升与投影 */
export function TaskCard({
  task,
  onOpen,
}: {
  task: BoardTask;
  onOpen: (id: number) => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: task.id, data: { type: "task", task } });

  const style: React.CSSProperties = {
    transform: CSS.Translate.toString(transform),
    transition,
  };

  // 逾期判断：时间经 useNow 订阅，保持渲染纯净（同 TaskListItem）
  const now = useNow();
  const overdue =
    task.dueDate != null && task.status !== "DONE" && Date.parse(task.dueDate) < now;

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      onClick={() => onOpen(task.id)}
      className={cn(
        "group cursor-pointer rounded-[var(--radius-md)] border border-hairline bg-surface p-3",
        "shadow-xs transition-all duration-200 easing-apple",
        "hover:border-line-strong hover:shadow-md hover:-translate-y-px",
        "focus-visible:outline-none focus-visible:shadow-[var(--shadow-focus)]",
        isDragging && "opacity-50 shadow-lg rotate-[1.5deg]"
      )}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen(task.id);
        }
      }}
      aria-label={`任务：${task.title}`}
    >
      <div className="flex items-start gap-2">
        <PriorityDot priority={task.priority} className="mt-1.5" />
        <p className="min-w-0 flex-1 text-[13.5px] font-medium leading-snug text-fg">
          {task.title}
        </p>
      </div>

      {/* 标签 */}
      {task.tags && task.tags.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1 pl-4">
          {task.tags.slice(0, 3).map((t) => (
            <span
              key={t.id}
              className="inline-flex items-center rounded-full px-1.5 py-0.5 text-[11px] font-medium"
              style={{ backgroundColor: `${t.color}1A`, color: t.color }}
            >
              {t.name}
            </span>
          ))}
          {task.tags.length > 3 ? (
            <span className="text-[11px] text-fg-muted">+{task.tags.length - 3}</span>
          ) : null}
        </div>
      ) : null}

      {/* 底部元数据 */}
      <div className="mt-2.5 flex items-center gap-2 pl-4">
        {task.projectKey ? (
          <span className="text-[11px] tabular-nums text-fg-muted">
            {task.projectKey}-{task.taskNumber}
          </span>
        ) : null}

        {task.storyPoints ? (
          <span className="inline-flex items-center gap-0.5 rounded bg-surface-3 px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-fg-secondary">
            <Flame className="size-3" />
            {task.storyPoints}
          </span>
        ) : null}

        {task.dueDate ? (
          <span
            className={cn(
              "inline-flex items-center gap-1 text-[11px] tabular-nums",
              overdue ? "text-danger" : "text-fg-muted"
            )}
          >
            <CalendarDays className="size-3" />
            {task.dueDate}
          </span>
        ) : null}

        <div className="ml-auto">
          <UserAvatar name={task.assigneeName} size={20} />
        </div>
      </div>
    </div>
  );
}

/** 拖拽时的卡片预览（DragOverlay 用） */
export function TaskCardPreview({ task }: { task: BoardTask }) {
  return (
    <div className="w-[268px] rotate-2 rounded-[var(--radius-md)] border border-hairline bg-surface p-3 shadow-lg">
      <div className="flex items-start gap-2">
        <PriorityDot priority={task.priority} className="mt-1.5" />
        <p className="min-w-0 flex-1 text-[13.5px] font-medium leading-snug text-fg">
          {task.title}
        </p>
      </div>
    </div>
  );
}
