"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarRange, Plus, Sparkles, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { PageShell } from "@/components/layout/PageShell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Textarea, Label } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, ContextRequired } from "@/components/ui/empty-state";
import { iterationApi, taskApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";
import { cn } from "@/lib/utils";
import type { Iteration, IterationStats } from "@/lib/types";

/**
 * 迭代管理
 * 卡片列表 → 点开即详情（含任务、复盘与 AI 草稿），不再多层下钻。
 */
export default function IterationsPage() {
  const queryClient = useQueryClient();
  const { projectId, projects } = useContextStore();
  const [createOpen, setCreateOpen] = React.useState(false);
  const [selected, setSelected] = React.useState<number | null>(null);
  const [form, setForm] = React.useState({ name: "", startDate: "", endDate: "" });

  const project = projects.find((p) => p.id === projectId);

  const {
    data: iterations = [],
    isLoading,
    isError,
    refetch,
  } = useQuery<Iteration[]>({
    queryKey: ["iterations", projectId],
    queryFn: () => iterationApi.list(projectId!),
    enabled: !!projectId,
  });

  const create = useMutation({
    mutationFn: () =>
      iterationApi.create(projectId!, {
        name: form.name.trim(),
        startDate: form.startDate || undefined,
        endDate: form.endDate || undefined,
        status: "PLANNED",
      }),
    onSuccess: () => {
      toast.success("迭代已创建");
      setCreateOpen(false);
      setForm({ name: "", startDate: "", endDate: "" });
      void queryClient.invalidateQueries({ queryKey: ["iterations", projectId] });
    },
    onError: (e: Error) => toast.error(e.message || "创建失败"),
  });

  const remove = useMutation({
    mutationFn: (id: number) => iterationApi.remove(projectId!, id),
    onSuccess: () => {
      toast.success("迭代已删除");
      setSelected(null);
      void queryClient.invalidateQueries({ queryKey: ["iterations", projectId] });
    },
    onError: (e: Error) => toast.error(e.message || "删除失败"),
  });

  if (!projectId) {
    return (
      <PageShell title="迭代">
        <ContextRequired what="项目" />
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell title="迭代">
        <ErrorState onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  const current = iterations.find((i) => i.id === selected) ?? null;

  return (
    <PageShell
      title="迭代"
      subtitle={project ? `${project.name} · 冲刺规划与复盘` : undefined}
      actions={
        <Button variant="primary" size="sm" onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" />
          新建迭代
        </Button>
      }
    >
      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-36 w-full" />
          ))}
        </div>
      ) : iterations.length === 0 ? (
        <Card>
          <EmptyState
            icon={<CalendarRange className="size-6" />}
            title="还没有迭代"
            description="创建迭代来规划冲刺周期并跟踪速率"
            action={
              <Button variant="primary" size="sm" onClick={() => setCreateOpen(true)}>
                新建迭代
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {iterations.map((it) => (
            <IterationCard key={it.id} iteration={it} onOpen={() => setSelected(it.id)} />
          ))}
        </div>
      )}

      {/* 详情抽屉式弹窗（单层，不做二级面板） */}
      <Dialog open={!!current} onOpenChange={(o) => !o && setSelected(null)}>
        <DialogContent size="lg">
          {current ? (
            // key 绑定迭代 ID：切换迭代即重挂载详情，重置复盘编辑态
            <IterationDetail
              key={current.id}
              projectId={projectId}
              iteration={current}
              onDelete={() => remove.mutate(current.id)}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      {/* 新建迭代 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent size="sm">
          <DialogHeader>
            <DialogTitle>新建迭代</DialogTitle>
            <DialogDescription>设置冲刺周期，之后可将任务规划进来</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <Label htmlFor="it-name">迭代名称</Label>
              <Input
                id="it-name"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="例如：Sprint 12"
                autoFocus
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="it-start">开始日期</Label>
                <Input
                  id="it-start"
                  type="date"
                  value={form.startDate}
                  onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))}
                />
              </div>
              <div>
                <Label htmlFor="it-end">结束日期</Label>
                <Input
                  id="it-end"
                  type="date"
                  value={form.endDate}
                  onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setCreateOpen(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={() => create.mutate()} disabled={!form.name.trim()}>
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageShell>
  );
}

/* ---------- 迭代卡片 ---------- */

function IterationCard({
  iteration,
  onOpen,
}: {
  iteration: Iteration;
  onOpen: () => void;
}) {
  const total = iteration.totalPoints ?? 0;
  const done = iteration.completedPoints ?? 0;
  const pct = total > 0 ? Math.round((done / total) * 100) : 0;
  const active = iteration.status === "ACTIVE";

  return (
    <Card
      interactive
      className={cn("p-4", active && "border-accent")}
      onClick={onOpen}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen();
        }
      }}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-[14px] font-semibold text-fg">{iteration.name}</p>
          <p className="mt-0.5 text-[11.5px] tabular-nums text-fg-muted">
            {iteration.startDate ?? "—"} → {iteration.endDate ?? "—"}
          </p>
        </div>
        <Badge tone={active ? "accent" : "neutral"} size="sm">
          {active ? "进行中" : (iteration.status ?? "已规划")}
        </Badge>
      </div>

      <div className="mt-4">
        <div className="mb-1.5 flex items-center justify-between text-[12px]">
          <span className="text-fg-secondary">故事点</span>
          <span className="tabular-nums text-fg-secondary">
            {done} / {total}
          </span>
        </div>
        <Progress value={pct} tone={pct >= 100 ? "done" : "accent"} />
      </div>

      {iteration.taskCount != null ? (
        <p className="mt-3 text-[12px] text-fg-muted">
          {iteration.doneCount ?? 0} / {iteration.taskCount} 个任务已完成
        </p>
      ) : null}
    </Card>
  );
}

/* ---------- 迭代详情 ---------- */

function IterationDetail({
  projectId,
  iteration,
  onDelete,
}: {
  projectId: number;
  iteration: Iteration;
  onDelete: () => void;
}) {
  const queryClient = useQueryClient();
  const { openTask } = useUiStore();

  /*
   * 复盘文本与 AI 草稿的初值直接取自迭代数据。
   * 调用方以 key={iteration.id} 挂载本组件，切换到另一个迭代时会整体重挂载，
   * 因此无需 useEffect 把 props 同步进 state。
   */
  const [retro, setRetro] = React.useState(iteration.retroContent ?? "");
  const [aiDraft, setAiDraft] = React.useState("");

  const { data: stats } = useQuery<IterationStats>({
    queryKey: ["iteration-stats", iteration.id],
    queryFn: () => iterationApi.stats(projectId, iteration.id),
  });

  const { data: tasks = [] } = useQuery({
    queryKey: ["board", projectId, iteration.id],
    queryFn: () => taskApi.board(projectId, iteration.id),
  });

  const saveRetro = useMutation({
    mutationFn: () => iterationApi.saveRetro(projectId, iteration.id, retro),
    onSuccess: () => {
      toast.success("复盘已保存");
      void queryClient.invalidateQueries({ queryKey: ["iterations", projectId] });
    },
    onError: (e: Error) => toast.error(e.message || "保存失败"),
  });

  const aiRetro = useMutation({
    mutationFn: () => iterationApi.retroAiPreview(projectId, iteration.id),
    onSuccess: (r) => {
      if (r?.available && r.draft) {
        setAiDraft(r.draft);
        toast.success("已生成复盘草稿");
      } else {
        toast.info(r?.message ?? "未配置 AI，无法生成草稿");
      }
    },
    onError: (e: Error) => toast.error(e.message || "生成失败"),
  });

  const aiReport = useMutation({
    mutationFn: () => iterationApi.reportAiPreview(projectId, iteration.id),
    onSuccess: (r) => {
      if (r?.available && r.summary) {
        setAiDraft(r.summary);
        toast.success("已生成周期摘要");
      } else {
        toast.info(r?.message ?? "未配置 AI，无法生成摘要");
      }
    },
    onError: (e: Error) => toast.error(e.message || "生成失败"),
  });

  const total = stats?.totalPoints ?? iteration.totalPoints ?? 0;
  const done = stats?.completedPoints ?? iteration.completedPoints ?? 0;

  return (
    <div>
      <DialogHeader>
        <div className="flex items-start justify-between gap-3">
          <div>
            <DialogTitle>{iteration.name}</DialogTitle>
            <DialogDescription>
              {iteration.startDate ?? "—"} → {iteration.endDate ?? "—"}
            </DialogDescription>
          </div>
          <Button variant="dangerGhost" size="iconSm" onClick={onDelete} aria-label="删除迭代">
            <Trash2 className="size-4" />
          </Button>
        </div>
      </DialogHeader>

      {/* 概览 */}
      <div className="mb-5 grid grid-cols-3 gap-3">
        <StatBox label="任务" value={`${stats?.done ?? 0}/${stats?.total ?? tasks.length}`} />
        <StatBox label="故事点" value={`${done}/${total}`} />
        <StatBox
          label="完成率"
          value={`${total > 0 ? Math.round((done / total) * 100) : 0}%`}
        />
      </div>

      {/* 任务清单 */}
      <Card className="mb-5 overflow-hidden">
        <CardHeader className="pb-2">
          <CardTitle>迭代任务</CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          {tasks.length === 0 ? (
            <p className="py-4 text-center text-[13px] text-fg-muted">该迭代还没有任务</p>
          ) : (
            <ul className="divide-y divide-hairline">
              {tasks.map((t) => (
                <li key={t.id}>
                  <button
                    type="button"
                    onClick={() => openTask(t.id)}
                    className="flex w-full items-center gap-2.5 py-2.5 text-left transition-colors hover:bg-surface-2"
                  >
                    <span className="min-w-0 flex-1 truncate text-[13.5px] text-fg">
                      {t.title}
                    </span>
                    <span className="shrink-0 text-[11.5px] tabular-nums text-fg-muted">
                      {t.storyPoints ? `${t.storyPoints} pt` : ""}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      {/* 复盘 */}
      <div className="mb-3 flex items-center gap-2">
        <Label className="mb-0">迭代复盘</Label>
        <div className="ml-auto flex gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => aiRetro.mutate()}
            disabled={aiRetro.isPending}
          >
            <Sparkles className="size-3.5" />
            AI 复盘草稿
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => aiReport.mutate()}
            disabled={aiReport.isPending}
          >
            <Sparkles className="size-3.5" />
            AI 周报摘要
          </Button>
        </div>
      </div>

      {aiDraft ? (
        <div className="mb-3 rounded-[var(--radius-md)] border border-hairline bg-accent-soft/50 p-3">
          <div className="mb-1.5 flex items-center justify-between">
            <span className="text-[12px] font-medium text-accent">AI 生成的草稿（未保存）</span>
            <div className="flex gap-1.5">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setRetro((r) => (r ? `${r}\n\n${aiDraft}` : aiDraft));
                  setAiDraft("");
                }}
              >
                采纳
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setAiDraft("")}>
                丢弃
              </Button>
            </div>
          </div>
          <p className="whitespace-pre-wrap text-[13px] leading-relaxed text-fg-secondary">
            {aiDraft}
          </p>
        </div>
      ) : null}

      <Textarea
        value={retro}
        onChange={(e) => setRetro(e.target.value)}
        placeholder="这次迭代做得好的是什么？哪些需要改进？"
        className="min-h-[120px]"
      />

      <DialogFooter>
        <Button
          variant="primary"
          onClick={() => saveRetro.mutate()}
          disabled={saveRetro.isPending}
        >
          保存复盘
        </Button>
      </DialogFooter>
    </div>
  );
}

function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[var(--radius-md)] border border-hairline bg-surface-2 px-3 py-2.5">
      <p className="text-[11.5px] text-fg-muted">{label}</p>
      <p className="mt-0.5 text-[16px] font-semibold tabular-nums text-fg">{value}</p>
    </div>
  );
}
