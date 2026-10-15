"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { AlertTriangle, Plus, X } from "lucide-react";
import { toast } from "sonner";
import { projectApi, taskApi, iterationApi } from "@/lib/api";
import { broadcastTaskChange } from "@/lib/realtime";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";
import { TaskCard, TaskCardPreview } from "@/components/board/TaskCard";
import { PageTitle, PageSubtitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";
import { SkeletonBoard } from "@/components/ui/skeleton";
import { ErrorState, ContextRequired } from "@/components/ui/empty-state";
import { cn } from "@/lib/utils";
import type { BoardColumn, BoardTask } from "@/lib/types";

type ColumnsState = Record<number, BoardTask[]>;

export default function BoardPage() {
  const queryClient = useQueryClient();
  const { projectId, projects } = useContextStore();
  const { openTask, setQuickCreateOpen } = useUiStore();
  const [iterationId, setIterationId] = React.useState<number | null>(null);
  const [activeTask, setActiveTask] = React.useState<BoardTask | null>(null);
  /**
   * 拖拽期间的本地覆盖态。
   *
   * 设计要点：看板的「唯一数据源」是服务端数据（derived），拖拽时用 override 做乐观预览；
   * 落库完成并重新拉取后清空 override，回到服务端真相。
   * 这样避免了「useEffect 里 setState 把服务端数据同步进本地 state」的反模式
   * （会触发级联渲染与 react-hooks/set-state-in-effect 报错）。
   */
  const [override, setOverride] = React.useState<ColumnsState | null>(null);

  const currentProject = projects.find((p) => p.id === projectId);

  const { data: cols = [], isLoading: loadingCols } = useQuery<BoardColumn[]>({
    queryKey: ["columns", projectId],
    queryFn: () => projectApi.columns(projectId!),
    enabled: !!projectId,
  });

  const { data: tasks = [], isLoading: loadingTasks, isError, refetch } = useQuery<
    BoardTask[]
  >({
    queryKey: ["board", projectId, iterationId],
    queryFn: () => taskApi.board(projectId!, iterationId),
    enabled: !!projectId,
  });

  const { data: iterations = [] } = useQuery({
    queryKey: ["iterations", projectId],
    queryFn: () => iterationApi.list(projectId!),
    enabled: !!projectId,
    staleTime: 60_000,
  });

  /**
   * 由服务端数据直接派生出「列 → 任务」分组（纯计算，不落 state）。
   * 后端 /projects/{pid}/board 返回的是扁平任务列表，前端按 boardColumnId 归组。
   */
  const derived: ColumnsState = React.useMemo(() => {
    const next: ColumnsState = {};
    if (!cols.length) return next;
    for (const c of cols) next[c.id] = [];
    for (const t of tasks) {
      // 未归列的任务兜底放进第一列，避免卡片“消失”
      const key = t.boardColumnId ?? cols[0]?.id;
      if (key != null && next[key]) next[key].push(t);
    }
    for (const id of Object.keys(next)) {
      next[Number(id)].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    }
    return next;
  }, [cols, tasks]);

  /** 实际渲染用的分组：拖拽中优先展示乐观覆盖态 */
  const columns: ColumnsState = override ?? derived;

  const moveMutation = useMutation({
    mutationFn: (args: { id: number; targetColumnId: number; afterTaskId: number | null }) =>
      taskApi.move(args.id, { targetColumnId: args.targetColumnId, afterTaskId: args.afterTaskId }),
    onError: (e: Error) => {
      toast.error(e.message || "移动失败");
      void refetch();
    },
    onSuccess: (_data, vars) => {
      // 广播给其他在线端，实现多端即时同步
      if (projectId) broadcastTaskChange(projectId, "move", vars.id);
      void queryClient.invalidateQueries({ queryKey: ["board", projectId] });
    },
    // 无论成功失败，落库流程结束后都撤掉乐观覆盖态，交回服务端数据
    onSettled: () => setOverride(null),
  });

  const sensors = useSensors(
    // 4px 阈值：区分「点击打开详情」与「拖拽」
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const findContainer = (id: string | number): number | null => {
    const sid = String(id);
    if (sid.startsWith("col-")) return Number(sid.slice(4));
    const numId = Number(sid);
    for (const [colId, list] of Object.entries(columns)) {
      if (list.some((t) => t.id === numId)) return Number(colId);
    }
    return null;
  };

  const onDragStart = (e: DragStartEvent) => {
    const id = Number(e.active.id);
    for (const list of Object.values(columns)) {
      const found = list.find((t) => t.id === id);
      if (found) {
        setActiveTask(found);
        return;
      }
    }
  };

  const onDragOver = (e: DragOverEvent) => {
    const { active, over } = e;
    if (!over) return;
    const activeId = Number(active.id);
    const from = findContainer(active.id);
    const to = findContainer(over.id);
    if (from == null || to == null || from === to) return;

    // 跨列：写入覆盖态做乐观预览（over.id 可能是卡片，也可能是空的列容器）
    setOverride((prev) => {
      const base = prev ?? derived;
      const source = [...(base[from] ?? [])];
      const dest = [...(base[to] ?? [])];
      const idx = source.findIndex((t) => t.id === activeId);
      if (idx < 0) return base;
      const [moved] = source.splice(idx, 1);
      const overIdx = dest.findIndex((t) => t.id === Number(over.id));
      const insertAt = overIdx >= 0 ? overIdx : dest.length;
      dest.splice(insertAt, 0, { ...moved, boardColumnId: to });
      return { ...base, [from]: source, [to]: dest };
    });
  };

  const onDragEnd = (e: DragEndEvent) => {
    setActiveTask(null);
    const { active, over } = e;
    if (!over) return;
    const activeId = Number(active.id);
    const from = findContainer(active.id);
    const to = findContainer(over.id);
    if (from == null || to == null) return;

    const base = override ?? derived;
    const list = [...(base[to] ?? [])];
    const oldIdx = list.findIndex((t) => t.id === activeId);
    const overIdx = list.findIndex((t) => t.id === Number(over.id));
    let next = list;

    if (from === to && oldIdx >= 0 && overIdx >= 0 && oldIdx !== overIdx) {
      // 同列内重排
      next = arrayMove(list, oldIdx, overIdx);
      setOverride({ ...base, [to]: next });
    } else if (oldIdx < 0) {
      // 卡片不在目标列表里：说明是跨列拖放，onDragOver 已写好覆盖态，此处无需改动
      return;
    }

    // 落库：目标列 + 新顺序。afterTaskId 指向移动后紧邻「其前」的卡片（null=置于列首），
    // 与后端 TaskMoveRequest 契约一致（targetColumnId 必填，避免「不能为null」校验失败）。
    const finalIdx = next.findIndex((t) => t.id === activeId);
    const afterId = finalIdx > 0 ? next[finalIdx - 1].id : null;
    moveMutation.mutate({
      id: activeId,
      targetColumnId: to,
      afterTaskId: afterId,
    });
  };

  if (!projectId) {
    return (
      <Shell>
        <ContextRequired
          what="项目"
          action={
            <Button variant="primary" size="sm" onClick={() => setQuickCreateOpen(true)}>
              新建任务
            </Button>
          }
        />
      </Shell>
    );
  }

  if (isError) {
    return (
      <Shell>
        <ErrorState onRetry={() => void refetch()} />
      </Shell>
    );
  }

  const loading = loadingCols || loadingTasks;

  return (
    <Shell
      title="看板"
      subtitle={currentProject ? `${currentProject.name} · 拖拽卡片调整状态` : undefined}
      actions={
        <div className="flex items-center gap-2">
          <div className="w-44">
            <Select
              value={iterationId ?? ""}
              onChange={(e) => setIterationId(e.target.value ? Number(e.target.value) : null)}
              aria-label="按迭代筛选"
            >
              <option value="">全部迭代</option>
              {iterations.map((it) => (
                <option key={it.id} value={it.id}>
                  {it.name}
                </option>
              ))}
            </Select>
          </div>
        </div>
      }
    >
      {loading ? (
        <SkeletonBoard />
      ) : (
        <DndContext
          sensors={sensors}
          collisionDetection={closestCorners}
          onDragStart={onDragStart}
          onDragOver={onDragOver}
          onDragEnd={onDragEnd}
        >
          <div className="flex gap-4 overflow-x-auto pb-4">
            {cols
              .slice()
              .sort((a, b) => a.sortOrder - b.sortOrder)
              .map((col) => (
                <Column
                  key={col.id}
                  column={col}
                  tasks={columns[col.id] ?? []}
                  onOpen={openTask}
                  onAdded={() =>
                    queryClient.invalidateQueries({ queryKey: ["board", projectId] })
                  }
                />
              ))}
          </div>

          <DragOverlay dropAnimation={{ duration: 200, easing: "cubic-bezier(0.32,0.72,0,1)" }}>
            {activeTask ? <TaskCardPreview task={activeTask} /> : null}
          </DragOverlay>
        </DndContext>
      )}
    </Shell>
  );
}

/* ---------- 列 ---------- */

function Column({
  column,
  tasks,
  onOpen,
  onAdded,
}: {
  column: BoardColumn;
  tasks: BoardTask[];
  onOpen: (id: number) => void;
  onAdded: () => void;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: `col-${column.id}` });
  const { projectId } = useContextStore();
  const [adding, setAdding] = React.useState(false);
  const [title, setTitle] = React.useState("");

  const create = useMutation({
    mutationFn: () =>
      taskApi.create(projectId!, {
        title: title.trim(),
        boardColumnId: column.id,
        status: column.mappedStatus ?? "TODO",
        priority: "P2",
      }),
    onSuccess: () => {
      setTitle("");
      onAdded();
    },
    onError: (e: Error) => toast.error(e.message || "创建失败"),
  });

  const overLimit =
    column.wipLimit != null && column.wipLimit > 0 && tasks.length > column.wipLimit;

  return (
    <div className="flex w-[292px] shrink-0 flex-col">
      {/* 列头 */}
      <div className="mb-2.5 flex items-center gap-2 px-1">
        <h3 className="text-[13.5px] font-semibold text-fg">{column.name}</h3>
        <span
          className={cn(
            "rounded-full px-1.5 py-0.5 text-[11px] font-medium tabular-nums",
            overLimit ? "bg-danger-soft text-danger" : "bg-surface-3 text-fg-secondary"
          )}
        >
          {tasks.length}
          {column.wipLimit ? ` / ${column.wipLimit}` : ""}
        </span>
        {overLimit ? (
          <AlertTriangle className="size-3.5 text-danger" aria-label="超出 WIP 限制" />
        ) : null}
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="ml-auto grid size-6 place-items-center rounded-[var(--radius-sm)] text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg"
          aria-label={`在「${column.name}」中添加任务`}
        >
          <Plus className="size-4" />
        </button>
      </div>

      {/* 列体 */}
      <div
        ref={setNodeRef}
        className={cn(
          "min-h-[140px] flex-1 space-y-2 rounded-[var(--radius-md)] p-2 transition-colors",
          isOver ? "bg-accent-soft" : "bg-surface-2"
        )}
      >
        <SortableContext
          items={tasks.map((t) => t.id)}
          strategy={verticalListSortingStrategy}
        >
          {tasks.map((t) => (
            <TaskCard key={t.id} task={t} onOpen={onOpen} />
          ))}
        </SortableContext>

        {/* 列内快速建卡：一步完成，不再弹多步向导 */}
        {adding ? (
          <div className="rounded-[var(--radius-md)] border border-hairline bg-surface p-2.5 shadow-sm">
            <Input
              autoFocus
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && title.trim()) create.mutate();
                if (e.key === "Escape") {
                  setAdding(false);
                  setTitle("");
                }
              }}
              placeholder="任务标题…"
            />
            <div className="mt-2 flex items-center justify-end gap-1.5">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setAdding(false);
                  setTitle("");
                }}
              >
                <X className="size-3.5" />
                取消
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={() => create.mutate()}
                disabled={!title.trim() || create.isPending}
              >
                添加
              </Button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="flex w-full items-center gap-1.5 rounded-[var(--radius-md)] px-2.5 py-2 text-[13px] text-fg-muted transition-colors hover:bg-surface-3 hover:text-fg-secondary"
          >
            <Plus className="size-3.5" />
            添加任务
          </button>
        )}

        {tasks.length === 0 && !adding ? (
          <p className="px-2.5 py-3 text-center text-[12px] text-fg-muted">暂无任务</p>
        ) : null}
      </div>
    </div>
  );
}

/* ---------- 页面容器 ---------- */

function Shell({
  children,
  title,
  subtitle,
  actions,
}: {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="px-5 py-6 lg:px-8 lg:py-8">
      {title ? (
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <PageTitle>{title}</PageTitle>
            {subtitle ? <PageSubtitle>{subtitle}</PageSubtitle> : null}
          </div>
          {actions}
        </div>
      ) : null}
      {children}
    </div>
  );
}
