"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { CalendarDays, Trash2, X } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogPortal, DialogOverlay } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input, Textarea, Select, Label } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { StatusBadge, PriorityBadge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { CommentSection } from "./CommentSection";
import { AttachmentSection } from "./AttachmentSection";
import { SubtaskSection } from "./SubtaskSection";
import { taskApi, teamApi, iterationApi } from "@/lib/api";
import { broadcastTaskChange } from "@/lib/realtime";
import { useUiStore } from "@/store/ui";
import { cn, fromNow } from "@/lib/utils";
import type { Task } from "@/lib/types";

/** 任务状态枚举与中文标签（值与后端 Task.status 严格一致） */
const STATUSES = ["TODO", "IN_PROGRESS", "REVIEW", "DONE"] as const;
const STATUS_LABEL: Record<string, string> = {
  TODO: "待办",
  IN_PROGRESS: "进行中",
  REVIEW: "审查中",
  DONE: "已完成",
};
const PRIORITIES = ["P0", "P1", "P2", "P3", "P4", "P5"];

/**
 * 任务详情抽屉（入口组件）
 *
 * 交互定位：从看板/列表点击卡片即从右侧滑出，任务详情**不是独立页面**——
 * 用户始终停留在当前视图，不产生「进入详情 → 返回列表」的往返。
 *
 * 结构说明：这里只负责「取数 + 抽屉外壳 + 加载骨架」，真正的表单内容放在
 * 下方的 TaskPanelContent，并用 `key={task.id}` 强制在不同任务间重新挂载。
 * 这样标题/描述的编辑态可以直接用 useState 初始化，无需 useEffect 回填
 * （避免 react-hooks/set-state-in-effect 的级联渲染问题）。
 */
export function TaskPanel() {
  const { activeTaskId, closeTask } = useUiStore();
  const taskId = activeTaskId;

  const { data: task, isLoading } = useQuery<Task>({
    queryKey: ["task", taskId],
    queryFn: () => taskApi.get(taskId!),
    enabled: !!taskId,
  });

  return (
    <Dialog open={!!taskId} onOpenChange={(open) => !open && closeTask()}>
      <DialogPortal>
        <DialogOverlay />
        <DialogPrimitive.Content
          className={cn(
            "fixed right-0 top-0 z-50 flex h-full w-full flex-col",
            "sm:w-[520px] border-l border-hairline bg-surface shadow-lg outline-none",
            "data-[state=open]:animate-in data-[state=closed]:animate-out",
            "data-[state=open]:slide-in-from-right data-[state=closed]:slide-out-to-right",
            "duration-300 easing-apple"
          )}
          aria-label="任务详情"
        >
          {isLoading || !task ? (
            <div className="space-y-4 p-6">
              <Skeleton className="h-6 w-3/4" />
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : (
            <TaskPanelContent key={task.id} task={task} />
          )}
        </DialogPrimitive.Content>
      </DialogPortal>
    </Dialog>
  );
}

/**
 * 任务详情内容
 *
 * 字段保存策略：标题/描述为「失焦保存」，其余字段（状态、优先级、负责人、
 * 迭代、日期、故事点）为「变更即保存」，均走 PATCH /tasks/{id}。
 * 保存成功后广播给其他在线端（见 broadcastTaskChange），实现多端即时同步。
 */
function TaskPanelContent({ task }: { task: Task }) {
  const queryClient = useQueryClient();
  const closeTask = useUiStore((s) => s.closeTask);
  const taskId = task.id;

  // 下拉选项：团队成员与项目迭代（依赖任务所属团队/项目）
  const { data: members = [] } = useQuery({
    queryKey: ["members", task.teamId],
    queryFn: () => teamApi.members(task.teamId),
    enabled: !!task.teamId,
    staleTime: 60_000,
  });

  const { data: iterations = [] } = useQuery({
    queryKey: ["iterations", task.projectId],
    queryFn: () => iterationApi.list(task.projectId),
    enabled: !!task.projectId,
    staleTime: 60_000,
  });

  /**
   * 标题与描述的本地编辑态。
   * 因父层用 key={task.id} 挂载本组件，切换任务时会整体重挂载，
   * 初值直接取任务数据即可，无需任何同步副作用。
   */
  const [title, setTitle] = React.useState(task.title ?? "");
  const [description, setDescription] = React.useState(task.description ?? "");

  /** 任一字段变更后需要刷新的缓存 */
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["task", taskId] });
    void queryClient.invalidateQueries({ queryKey: ["board"] });
    void queryClient.invalidateQueries({ queryKey: ["tasks"] });
    void queryClient.invalidateQueries({ queryKey: ["my-tasks"] });
  };

  const update = useMutation({
    mutationFn: (data: Partial<Task>) => taskApi.patch(taskId, data),
    onSuccess: () => {
      invalidate();
      broadcastTaskChange(task.projectId, "update", taskId);
      toast.success("已保存");
    },
    onError: (e: Error) => toast.error(e.message || "保存失败"),
  });

  const remove = useMutation({
    mutationFn: () => taskApi.remove(taskId),
    onSuccess: () => {
      toast.success("任务已删除");
      invalidate();
      closeTask();
    },
    onError: (e: Error) => toast.error(e.message || "删除失败"),
  });

  const saveTitle = () => {
    const next = title.trim();
    if (!next || next === task.title) {
      setTitle(task.title ?? ""); // 非法或没改动则回滚
      return;
    }
    update.mutate({ title: next });
  };

  const saveDescription = () => {
    if ((description ?? "") === (task.description ?? "")) return;
    update.mutate({ description });
  };

  const confirmRemove = () => {
    if (window.confirm("确定删除该任务？此操作不可撤销。")) remove.mutate();
  };

  return (
    <>
      {/* 头部：编号 / 状态 / 优先级 + 删除与关闭 */}
      <div className="flex items-start justify-between gap-3 border-b border-hairline px-6 py-4">
        <div className="min-w-0">
          <div className="mb-1.5 flex items-center gap-2">
            <span className="text-[12px] tabular-nums text-fg-muted">
              #{task.taskNumber ?? task.id}
            </span>
            <StatusBadge status={task.status} size="sm" />
            <PriorityBadge priority={task.priority} size="sm" />
          </div>
          <p className="text-[11.5px] text-fg-muted">创建于 {fromNow(task.createdAt)}</p>
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button variant="dangerGhost" size="iconSm" aria-label="删除任务" onClick={confirmRemove}>
            <Trash2 className="size-4" />
          </Button>
          <DialogPrimitive.Close
            className="grid size-7 place-items-center rounded-full text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg"
            aria-label="关闭"
          >
            <X className="size-4" />
          </DialogPrimitive.Close>
        </div>
      </div>

      {/* 标题 + 描述（失焦保存） */}
      <div className="space-y-3 border-b border-hairline px-6 py-4">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={saveTitle}
          onKeyDown={(e) => {
            if (e.key === "Enter") e.currentTarget.blur();
          }}
          className="w-full bg-transparent text-[19px] font-semibold leading-snug tracking-[-0.02em] text-fg outline-none placeholder:text-fg-muted"
          placeholder="任务标题"
          aria-label="任务标题"
        />
        <Textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          onBlur={saveDescription}
          placeholder="添加描述…"
          className="min-h-[88px]"
          aria-label="任务描述"
        />
      </div>

      {/* 属性区：两列平铺，不做折叠二级面板 */}
      <div className="grid grid-cols-2 gap-x-4 gap-y-3 border-b border-hairline px-6 py-4">
        <div>
          <Label htmlFor="tp-status">状态</Label>
          <Select
            id="tp-status"
            value={task.status}
            onChange={(e) => update.mutate({ status: e.target.value })}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <Label htmlFor="tp-priority">优先级</Label>
          <Select
            id="tp-priority"
            value={task.priority}
            onChange={(e) => update.mutate({ priority: e.target.value })}
          >
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <Label htmlFor="tp-assignee">负责人</Label>
          <Select
            id="tp-assignee"
            value={task.assigneeId ?? ""}
            onChange={(e) =>
              update.mutate({ assigneeId: e.target.value ? Number(e.target.value) : null })
            }
          >
            <option value="">未分配</option>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.displayName ?? m.email ?? `用户 ${m.userId}`}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <Label htmlFor="tp-iteration">迭代</Label>
          <Select
            id="tp-iteration"
            value={task.iterationId ?? ""}
            onChange={(e) =>
              update.mutate({ iterationId: e.target.value ? Number(e.target.value) : null })
            }
          >
            <option value="">未规划</option>
            {iterations.map((it) => (
              <option key={it.id} value={it.id}>
                {it.name}
              </option>
            ))}
          </Select>
        </div>

        <div>
          <Label htmlFor="tp-start">开始日期</Label>
          <Input
            id="tp-start"
            type="date"
            value={task.startDate ?? ""}
            onChange={(e) => update.mutate({ startDate: e.target.value || null })}
          />
        </div>

        <div>
          <Label htmlFor="tp-due">截止日期</Label>
          <Input
            id="tp-due"
            type="date"
            value={task.dueDate ?? ""}
            onChange={(e) => update.mutate({ dueDate: e.target.value || null })}
          />
        </div>

        <div>
          <Label htmlFor="tp-points">故事点</Label>
          <Input
            id="tp-points"
            type="number"
            min={0}
            value={task.storyPoints ?? ""}
            onChange={(e) =>
              update.mutate({ storyPoints: e.target.value ? Number(e.target.value) : null })
            }
            placeholder="—"
          />
        </div>

        <div className="flex items-end">
          <div className="flex items-center gap-1.5 text-[12px] text-fg-muted">
            <CalendarDays className="size-3.5" />
            更新于 {fromNow(task.updatedAt)}
          </div>
        </div>
      </div>

      {/* 分区：评论 / 子任务 / 附件 */}
      <div className="min-h-0 flex-1 overflow-hidden">
        <Tabs defaultValue="comments" className="flex h-full flex-col">
          <div className="px-6 pt-4">
            <TabsList>
              <TabsTrigger value="comments">评论</TabsTrigger>
              <TabsTrigger value="subtasks">子任务</TabsTrigger>
              <TabsTrigger value="attachments">附件</TabsTrigger>
            </TabsList>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">
            <TabsContent value="comments">
              <CommentSection taskId={taskId} />
            </TabsContent>
            <TabsContent value="subtasks">
              <SubtaskSection task={task} onChanged={invalidate} />
            </TabsContent>
            <TabsContent value="attachments">
              <AttachmentSection taskId={taskId} />
            </TabsContent>
          </div>
        </Tabs>
      </div>
    </>
  );
}
