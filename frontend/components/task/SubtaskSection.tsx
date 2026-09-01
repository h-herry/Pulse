"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { taskApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { cn } from "@/lib/utils";
import type { Task } from "@/lib/types";

/** 子任务：父任务内联增删与勾选完成 */
export function SubtaskSection({
  task,
  onChanged,
}: {
  task: Task;
  onChanged?: () => void;
}) {
  const queryClient = useQueryClient();
  const [title, setTitle] = React.useState("");

  const { data: subtasks = [], isLoading } = useQuery<Task[]>({
    queryKey: ["subtasks", task.id],
    queryFn: () => taskApi.subtasks(task.id),
    enabled: !!task.id,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["subtasks", task.id] });
    onChanged?.();
  };

  const add = useMutation({
    mutationFn: () =>
      taskApi.create(task.projectId, {
        title: title.trim(),
        parentId: task.id,
        status: "TODO",
        priority: task.priority,
        assigneeId: task.assigneeId,
      }),
    onSuccess: () => {
      setTitle("");
      refresh();
    },
    onError: (e: Error) => toast.error(e.message || "添加失败"),
  });

  const toggle = useMutation({
    mutationFn: (sub: Task) =>
      taskApi.patch(sub.id, { status: sub.status === "DONE" ? "TODO" : "DONE" }),
    onSuccess: refresh,
    onError: (e: Error) => toast.error(e.message || "更新失败"),
  });

  const remove = useMutation({
    mutationFn: (id: number) => taskApi.remove(id),
    onSuccess: refresh,
    onError: (e: Error) => toast.error(e.message || "删除失败"),
  });

  const done = subtasks.filter((s) => s.status === "DONE").length;

  return (
    <div className="pt-3">
      {/* 进度摘要 */}
      {subtasks.length > 0 ? (
        <div className="mb-3 flex items-center gap-2">
          <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-3">
            <div
              className="h-full rounded-full bg-done transition-[width] duration-500 easing-apple"
              style={{ width: `${(done / subtasks.length) * 100}%` }}
            />
          </div>
          <span className="text-[12px] tabular-nums text-fg-secondary">
            {done}/{subtasks.length}
          </span>
        </div>
      ) : null}

      {/* 快速添加 */}
      <div className="flex gap-2">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && title.trim()) add.mutate();
          }}
          placeholder="添加子任务…"
        />
        <Button
          variant="secondary"
          size="icon"
          onClick={() => title.trim() && add.mutate()}
          disabled={!title.trim() || add.isPending}
          aria-label="添加子任务"
        >
          <Plus className="size-4" />
        </Button>
      </div>

      <div className="mt-3 space-y-1">
        {isLoading ? (
          Array.from({ length: 2 }).map((_, i) => <Skeleton key={i} className="h-9 w-full" />)
        ) : subtasks.length === 0 ? (
          <EmptyState compact title="暂无子任务" description="把大任务拆成可执行的小步骤" />
        ) : (
          subtasks.map((s) => {
            const isDone = s.status === "DONE";
            return (
              <div
                key={s.id}
                className="group flex items-center gap-2.5 rounded-[var(--radius-sm)] px-2 py-1.5 transition-colors hover:bg-surface-2"
              >
                <button
                  type="button"
                  onClick={() => toggle.mutate(s)}
                  className={cn(
                    "grid size-4 shrink-0 place-items-center rounded-[5px] border transition-colors",
                    isDone
                      ? "border-done bg-done text-white"
                      : "border-line-strong hover:border-accent"
                  )}
                  aria-label={isDone ? "标记为未完成" : "标记为已完成"}
                >
                  {isDone ? <Check className="size-3" strokeWidth={3} /> : null}
                </button>
                <span
                  className={cn(
                    "min-w-0 flex-1 truncate text-[13.5px]",
                    isDone ? "text-fg-muted line-through" : "text-fg"
                  )}
                >
                  {s.title}
                </span>
                <button
                  type="button"
                  onClick={() => remove.mutate(s.id)}
                  className="text-fg-muted opacity-0 transition-opacity hover:text-danger group-hover:opacity-100"
                  aria-label="删除子任务"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
