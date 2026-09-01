"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle2, ListTodo, TrendingUp } from "lucide-react";
import { PageShell, Section } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Skeleton, SkeletonList } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, ContextRequired } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { TaskListItem } from "@/components/task/TaskListItem";
import { reportApi, teamApi, taskApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";
import { useAuthStore } from "@/store/auth";
import type { BoardTask, ProjectProgress, ReportOverview } from "@/lib/types";

/**
 * 总览
 * 一屏看清团队脉搏：关键指标 → 项目进度 → 我的进行中任务。
 * 取代原「必须先选团队再选项目才能看到任何东西」的空转流程。
 */
export default function OverviewPage() {
  const router = useRouter();
  const { teamId, teams, selectProject } = useContextStore();
  const { openTask } = useUiStore();
  const user = useAuthStore((s) => s.user);
  const team = teams.find((t) => t.id === teamId);

  const { data: overview, isLoading: loadingOverview, isError, refetch } = useQuery<
    ReportOverview
  >({
    queryKey: ["overview", teamId],
    queryFn: () => reportApi.overview(teamId!),
    enabled: !!teamId,
  });

  const { data: progress = [], isLoading: loadingProgress } = useQuery<ProjectProgress[]>({
    queryKey: ["projects-progress", teamId],
    queryFn: () => teamApi.projectsProgress(teamId!),
    enabled: !!teamId,
  });

  const { data: myTasks = [], isLoading: loadingMine } = useQuery<BoardTask[]>({
    queryKey: ["my-tasks"],
    queryFn: () => taskApi.my(),
    staleTime: 30_000,
  });

  if (!teamId) {
    return (
      <PageShell title="总览">
        <ContextRequired what="团队" />
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell title="总览">
        <ErrorState onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  const doing = myTasks.filter((t) => t.status === "IN_PROGRESS").slice(0, 8);

  return (
    <PageShell
      title="总览"
      subtitle={team ? `${team.name} · 团队任务脉搏` : undefined}
      actions={
        <Button variant="secondary" size="sm" onClick={() => router.push("/board")}>
          前往看板
        </Button>
      }
    >
      {/* 关键指标 */}
      <Section>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            icon={<ListTodo className="size-4" />}
            label="总任务"
            value={overview?.total}
            loading={loadingOverview}
          />
          <StatCard
            icon={<TrendingUp className="size-4" />}
            label="进行中"
            value={
              overview && overview.total != null && overview.done != null
                ? overview.total - overview.done
                : undefined
            }
            loading={loadingOverview}
          />
          <StatCard
            icon={<CheckCircle2 className="size-4" />}
            label="已完成"
            value={overview?.done}
            loading={loadingOverview}
            tone="done"
          />
          <StatCard
            icon={<AlertTriangle className="size-4" />}
            label="已逾期"
            value={overview?.overdue}
            loading={loadingOverview}
            tone="danger"
          />
        </div>
      </Section>

      <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
        {/* 项目进度 */}
        <Section title="项目进度" description="点击直接进入该项目的看板">
          <Card className="overflow-hidden">
            {loadingProgress ? (
              <div className="p-4">
                <SkeletonList rows={4} />
              </div>
            ) : progress.length === 0 ? (
              <EmptyState
                compact
                title="暂无项目"
                description="创建第一个项目开始协作"
                action={
                  <Button variant="primary" size="sm" onClick={() => router.push("/team")}>
                    去创建
                  </Button>
                }
              />
            ) : (
              <ul className="divide-y divide-hairline">
                {progress.map((p) => {
                  const pct = p.progress ?? 0;
                  return (
                    <li key={p.projectId}>
                      <button
                        type="button"
                        onClick={() => {
                          selectProject(p.projectId);
                          router.push("/board");
                        }}
                        className="flex w-full items-center gap-4 px-5 py-3.5 text-left transition-colors hover:bg-surface-2"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <span className="truncate text-[13.5px] font-medium text-fg">
                              {p.projectName}
                            </span>
                            <span className="shrink-0 text-[11px] uppercase text-fg-muted">
                              {p.projectKey}
                            </span>
                          </div>
                          <Progress
                            value={pct}
                            className="mt-2"
                            tone={pct >= 100 ? "done" : "accent"}
                          />
                        </div>
                        <span className="w-10 shrink-0 text-right text-[13px] font-semibold tabular-nums text-fg">
                          {pct}%
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </Section>

        {/* 我的进行中 */}
        <Section title="我的进行中" description={user?.displayName ?? undefined}>
          <Card className="overflow-hidden">
            {loadingMine ? (
              <div className="p-4">
                <SkeletonList rows={4} />
              </div>
            ) : doing.length === 0 ? (
              <EmptyState compact title="没有进行中的任务" description="去「我的任务」看看待办" />
            ) : (
              <div>
                {doing.map((t) => (
                  <TaskListItem key={t.id} task={t} onOpen={openTask} showProject />
                ))}
              </div>
            )}
          </Card>
        </Section>
      </div>
    </PageShell>
  );
}

/* ---------- 指标卡 ---------- */

function StatCard({
  icon,
  label,
  value,
  loading,
  tone = "accent",
}: {
  icon: React.ReactNode;
  label: string;
  value?: number;
  loading?: boolean;
  tone?: "accent" | "done" | "danger";
}) {
  const toneClass = {
    accent: "bg-accent-soft text-accent",
    done: "bg-done-soft text-done",
    danger: "bg-danger-soft text-danger",
  }[tone];

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <span className="text-[13px] text-fg-secondary">{label}</span>
        <span className={`grid size-7 place-items-center rounded-full ${toneClass}`}>
          {icon}
        </span>
      </div>
      {loading ? (
        <Skeleton className="mt-3 h-8 w-16" />
      ) : (
        <p className="mt-2 text-[28px] font-semibold leading-none tracking-[-0.03em] text-fg tabular-nums">
          {value ?? 0}
        </p>
      )}
    </Card>
  );
}
