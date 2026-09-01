"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import dayjs from "dayjs";
import { CalendarRange } from "lucide-react";
import { PageShell } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { StatusBadge, PriorityDot, STATUS_META } from "@/components/ui/badge";
import { UserAvatar } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, ContextRequired } from "@/components/ui/empty-state";
import { taskApi, iterationApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";
import { cn } from "@/lib/utils";
import type { Iteration } from "@/lib/types";

type Scale = "week" | "month" | "quarter";

/**
 * 交付时间线（甘特视图）
 *
 * 说明：后端列表接口仅暴露 dueDate（不含 startDate），因此本视图按
 * **截止日期**绘制排期条，并叠加迭代区间作为背景带；未设置截止日期的
 * 任务归入「未排期」分组。点击色条即从右侧抽屉打开任务详情。
 */
export default function TimelinePage() {
  const { projectId, projects } = useContextStore();
  const { openTask } = useUiStore();
  const [scale, setScale] = React.useState<Scale>("month");

  const project = projects.find((p) => p.id === projectId);

  const {
    data: page,
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["project-tasks", projectId],
    queryFn: () => taskApi.list(projectId!),
    enabled: !!projectId,
  });

  const { data: iterations = [] } = useQuery<Iteration[]>({
    queryKey: ["iterations", projectId],
    queryFn: () => iterationApi.list(projectId!),
    enabled: !!projectId,
    staleTime: 60_000,
  });

  if (!projectId) {
    return (
      <PageShell title="时间线">
        <ContextRequired what="项目" />
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell title="时间线">
        <ErrorState onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  const tasks = page?.records ?? [];
  const scheduled = tasks
    .filter((t) => !!t.dueDate)
    .sort((a, b) => (a.dueDate! < b.dueDate! ? -1 : 1));
  const unscheduled = tasks.filter((t) => !t.dueDate);

  const dates = scheduled.map((t) => dayjs(t.dueDate));
  const min = dates.length ? dates.reduce((a, b) => (a.isBefore(b) ? a : b)) : dayjs();
  const max = dates.length ? dates.reduce((a, b) => (a.isAfter(b) ? a : b)) : dayjs();
  const rangeStart = min.startOf(scale === "quarter" ? "month" : scale).subtract(1, "day");
  const rangeEnd = max.endOf(scale === "quarter" ? "month" : scale).add(1, "day");

  const totalDays = Math.max(1, rangeEnd.diff(rangeStart, "day"));
  const pxPerDay = scale === "week" ? 34 : scale === "month" ? 12 : 6;
  const width = totalDays * pxPerDay;

  // 时间刻度
  const ticks: { left: number; label: string }[] = [];
  const step = scale === "week" ? "day" : scale === "month" ? "week" : "month";
  for (let d = rangeStart.clone(); d.isBefore(rangeEnd); d = d.add(1, step as dayjs.ManipulateType)) {
    ticks.push({
      left: d.diff(rangeStart, "day") * pxPerDay,
      label: step === "day" ? d.format("M/D") : step === "week" ? d.format("M/D") : d.format("M月"),
    });
  }

  const todayLeft = dayjs().diff(rangeStart, "day") * pxPerDay;

  return (
    <PageShell
      title="时间线"
      subtitle={project ? `${project.name} · 按截止日期排布` : undefined}
      actions={
        <Tabs value={scale} onValueChange={(v) => setScale(v as Scale)}>
          <TabsList>
            <TabsTrigger value="week">周</TabsTrigger>
            <TabsTrigger value="month">月</TabsTrigger>
            <TabsTrigger value="quarter">季</TabsTrigger>
          </TabsList>
        </Tabs>
      }
    >
      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : tasks.length === 0 ? (
        <Card>
          <EmptyState
            icon={<CalendarRange className="size-6" />}
            title="还没有任务"
            description="为任务设置截止日期后即可在此查看交付节奏"
          />
        </Card>
      ) : (
        <Card className="overflow-hidden">
          {/* 时间轴 */}
          <div className="overflow-x-auto">
            <div style={{ width: Math.max(width + 280, 640) }}>
              <div className="relative h-9 border-b border-hairline bg-surface-2">
                <div className="sticky left-0 z-10 inline-flex h-9 w-[260px] items-center border-r border-hairline bg-surface-2 px-4 text-[11.5px] font-medium text-fg-secondary">
                  任务
                </div>
                {ticks.map((t, i) => (
                  <span
                    key={i}
                    className="absolute top-2.5 text-[11px] tabular-nums text-fg-muted"
                    style={{ left: 260 + t.left }}
                  >
                    {t.label}
                  </span>
                ))}
                {todayLeft >= 0 && todayLeft <= width ? (
                  <span
                    className="absolute bottom-0 top-0 w-px bg-danger"
                    style={{ left: 260 + todayLeft }}
                    aria-label="今天"
                  />
                ) : null}
              </div>

              {/* 迭代背景带 */}
              {iterations.map((it) => {
                if (!it.startDate || !it.endDate) return null;
                const left = Math.max(0, dayjs(it.startDate).diff(rangeStart, "day")) * pxPerDay;
                const w =
                  Math.max(1, dayjs(it.endDate).diff(dayjs(it.startDate), "day")) * pxPerDay;
                return (
                  <div key={it.id} className="relative h-6 border-b border-hairline">
                    <div className="sticky left-0 z-10 inline-flex h-6 w-[260px] items-center gap-1.5 border-r border-hairline bg-surface px-4">
                      <CalendarRange className="size-3 text-accent" />
                      <span className="truncate text-[11.5px] font-medium text-fg">
                        {it.name}
                      </span>
                    </div>
                    <div
                      className="absolute top-1 h-4 rounded-[4px] bg-accent-soft"
                      style={{ left: 260 + left, width: w }}
                    />
                  </div>
                );
              })}

              {/* 任务条 */}
              {scheduled.map((t) => {
                const left =
                  Math.max(0, dayjs(t.dueDate).diff(rangeStart, "day")) * pxPerDay;
                const meta = STATUS_META[t.status ?? ""] ?? STATUS_META.TODO;
                const overdue = t.status !== "DONE" && dayjs(t.dueDate).isBefore(dayjs(), "day");
                return (
                  <div
                    key={t.id}
                    className="group relative h-11 border-b border-hairline last:border-b-0 hover:bg-surface-2"
                  >
                    <button
                      type="button"
                      onClick={() => openTask(t.id)}
                      className="sticky left-0 z-10 flex h-11 w-[260px] items-center gap-2 border-r border-hairline bg-surface px-4 text-left group-hover:bg-surface-2"
                    >
                      <PriorityDot priority={t.priority} />
                      <span className="min-w-0 flex-1 truncate text-[13px] text-fg">
                        {t.title}
                      </span>
                      <UserAvatar name={t.assigneeName} size={20} />
                    </button>
                    <button
                      type="button"
                      onClick={() => openTask(t.id)}
                      className={cn(
                        "absolute top-2.5 h-6 min-w-[10px] rounded-full px-2 text-[11px] leading-6 text-white transition-opacity hover:opacity-85",
                        overdue ? "bg-danger" : meta.dot
                      )}
                      style={{ left: 260 + left }}
                      title={`${t.title} · 截止 ${t.dueDate}`}
                    >
                      {t.dueDate}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        </Card>
      )}

      {/* 未排期 */}
      {unscheduled.length > 0 ? (
        <div className="mt-6">
          <h2 className="mb-3 text-[15px] font-semibold text-fg">
            未设置截止日期
            <span className="ml-2 text-[12px] font-normal text-fg-muted">
              {unscheduled.length} 个
            </span>
          </h2>
          <Card className="overflow-hidden">
            <ul className="divide-y divide-hairline">
              {unscheduled.map((t) => (
                <li key={t.id}>
                  <button
                    type="button"
                    onClick={() => openTask(t.id)}
                    className="flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors hover:bg-surface-2"
                  >
                    <PriorityDot priority={t.priority} />
                    <span className="min-w-0 flex-1 truncate text-[13.5px] text-fg">
                      {t.title}
                    </span>
                    <StatusBadge status={t.status} size="sm" />
                    <UserAvatar name={t.assigneeName} size={20} />
                  </button>
                </li>
              ))}
            </ul>
          </Card>
        </div>
      ) : null}
    </PageShell>
  );
}
