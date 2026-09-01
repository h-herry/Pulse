"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Download } from "lucide-react";
import { PageShell } from "@/components/layout/PageShell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/input";
import { ErrorState, ContextRequired } from "@/components/ui/empty-state";
import { reportApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import type {
  CycleTimeResponse,
  ReportOverview,
  TrendPoint,
  VelocityPoint,
} from "@/lib/types";

/**
 * 报表
 * 一页看全：关键指标 + 燃尽 + 速率 + 分布 + 周期 + 成员效率。
 * 用 Recharts 统一视觉语言（细网格、圆角、品牌色）。
 */
export default function ReportsPage() {
  const { teamId, projectId, projects } = useContextStore();

  const { data: overview, isError, refetch } = useQuery<ReportOverview>({
    queryKey: ["overview", teamId],
    queryFn: () => reportApi.overview(teamId!),
    enabled: !!teamId,
  });

  const { data: trend } = useQuery({
    queryKey: ["report-trend", teamId, projectId],
    queryFn: () => reportApi.trend(teamId!, projectId ?? undefined),
    enabled: !!teamId,
  });

  const { data: velocity = [] } = useQuery<VelocityPoint[]>({
    queryKey: ["report-velocity", teamId, projectId],
    queryFn: () => reportApi.velocity(teamId!, projectId ?? undefined),
    enabled: !!teamId,
  });

  const { data: statusDist } = useQuery({
    queryKey: ["report-status", teamId, projectId],
    queryFn: () => reportApi.statusDistribution(teamId!, projectId ?? undefined),
    enabled: !!teamId,
  });

  const { data: priorityDist } = useQuery({
    queryKey: ["report-priority", teamId, projectId],
    queryFn: () => reportApi.priorityDistribution(teamId!, projectId ?? undefined),
    enabled: !!teamId,
  });

  const { data: cycle } = useQuery<CycleTimeResponse>({
    queryKey: ["report-cycle", teamId, projectId],
    queryFn: () => reportApi.cycleTime(teamId!, projectId ?? undefined),
    enabled: !!teamId,
  });

  if (!teamId) {
    return (
      <PageShell title="报表">
        <ContextRequired what="团队" />
      </PageShell>
    );
  }

  if (isError) {
    return (
      <PageShell title="报表">
        <ErrorState onRetry={() => void refetch()} />
      </PageShell>
    );
  }

  // 归一化燃尽数据（后端字段可能存在多种命名）
  const trendData: TrendPoint[] = (trend?.data ?? []).map((p) => ({
    date: p.date ?? p.snapshotDate ?? "",
    remaining: p.remaining ?? p.remainingPoints ?? 0,
    ideal: p.ideal ?? p.idealPoints ?? 0,
  }));

  const statusData = (statusDist?.items ?? []).map((i) => ({
    name: STATUS_LABELS[i.status ?? ""] ?? (i.status ?? "未知"),
    value: i.count ?? 0,
    color: STATUS_COLORS[i.status ?? ""] ?? "var(--text-muted)",
  }));

  const priorityData = (priorityDist?.items ?? []).map((i) => ({
    name: i.priority ?? "未知",
    value: i.count ?? 0,
    color: PRIORITY_COLORS[i.priority ?? ""] ?? "var(--text-muted)",
  }));

  const cycleData = (cycle?.buckets ?? []).map((b) => ({
    name: b.range ?? "",
    count: b.count ?? 0,
  }));

  return (
    <PageShell
      title="报表"
      subtitle="团队交付节奏与质量的全景视图"
      actions={
        <div className="flex items-center gap-2">
          <div className="w-44">
            <Select
              value={projectId ?? ""}
              onChange={(e) => useContextStore.getState().selectProject(Number(e.target.value))}
              aria-label="切换项目"
            >
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </div>
          <Button variant="secondary" size="sm" asChild>
            <a href={reportApi.exportUrl(teamId, projectId ?? undefined)} download>
              <Download className="size-4" />
              导出
            </a>
          </Button>
        </div>
      }
    >
      {/* 关键指标 */}
      <div className="mb-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric label="总任务" value={overview?.total ?? 0} />
        <Metric label="已完成" value={overview?.done ?? 0} tone="done" />
        <Metric label="已逾期" value={overview?.overdue ?? 0} tone="danger" />
        <Metric label="完成率" value={`${overview?.progress ?? 0}%`} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <ChartCard title="燃尽趋势" subtitle="剩余工作量与理想线的对比">
          {trendData.length === 0 ? (
            <ChartEmpty />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={trendData} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--hairline)" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={{ stroke: "var(--hairline)" }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip contentStyle={tooltipStyle} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Line
                  type="monotone"
                  dataKey="ideal"
                  name="理想剩余"
                  stroke="var(--text-muted)"
                  strokeDasharray="4 4"
                  strokeWidth={1.5}
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="remaining"
                  name="实际剩余"
                  stroke="var(--accent)"
                  strokeWidth={2.5}
                  dot={{ r: 2.5 }}
                  activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="迭代速率" subtitle="每个迭代完成的故事点">
          {velocity.length === 0 ? (
            <ChartEmpty />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={velocity} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--hairline)" vertical={false} />
                <XAxis
                  dataKey="name"
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={{ stroke: "var(--hairline)" }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "var(--surface-2)" }} />
                <Bar
                  dataKey="completedStoryPoints"
                  name="完成故事点"
                  fill="var(--accent)"
                  radius={[6, 6, 0, 0]}
                  maxBarSize={44}
                />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="状态分布">
          {statusData.length === 0 ? (
            <ChartEmpty />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={statusData}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={52}
                  outerRadius={84}
                  paddingAngle={3}
                  stroke="none"
                >
                  {statusData.map((d, i) => (
                    <Cell key={i} fill={d.color} />
                  ))}
                </Pie>
                <Tooltip contentStyle={tooltipStyle} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="优先级分布">
          {priorityData.length === 0 ? (
            <ChartEmpty />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={priorityData}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={52}
                  outerRadius={84}
                  paddingAngle={3}
                  stroke="none"
                >
                  {priorityData.map((d, i) => (
                    <Cell key={i} fill={d.color} />
                  ))}
                </Pie>
                <Tooltip contentStyle={tooltipStyle} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </ChartCard>

        <ChartCard title="周期时间分布" subtitle="任务从创建到完成的耗时">
          {cycleData.length === 0 ? (
            <ChartEmpty />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={cycleData} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--hairline)" vertical={false} />
                <XAxis
                  dataKey="name"
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={{ stroke: "var(--hairline)" }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fontSize: 11, fill: "var(--text-muted)" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "var(--surface-2)" }} />
                <Bar
                  dataKey="count"
                  name="任务数"
                  fill="var(--status-doing)"
                  radius={[6, 6, 0, 0]}
                  maxBarSize={44}
                />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>
    </PageShell>
  );
}

/* ---------- 子组件 ---------- */

const tooltipStyle: React.CSSProperties = {
  background: "var(--surface)",
  border: "1px solid var(--hairline)",
  borderRadius: "var(--radius-md)",
  boxShadow: "var(--shadow-md)",
  fontSize: 12,
  color: "var(--text-primary)",
};

const STATUS_LABELS: Record<string, string> = {
  TODO: "待办",
  IN_PROGRESS: "进行中",
  REVIEW: "审查中",
  DONE: "已完成",
};

const STATUS_COLORS: Record<string, string> = {
  TODO: "var(--status-todo)",
  IN_PROGRESS: "var(--status-doing)",
  REVIEW: "var(--status-review)",
  DONE: "var(--status-done)",
};

const PRIORITY_COLORS: Record<string, string> = {
  P0: "var(--prio-p0)",
  P1: "var(--prio-p1)",
  P2: "var(--prio-p2)",
  P3: "var(--prio-p3)",
  P4: "var(--prio-p4)",
  P5: "var(--prio-p5)",
};

function Metric({
  label,
  value,
  tone = "default",
}: {
  label: string;
  value: number | string;
  tone?: "default" | "done" | "danger";
}) {
  const color =
    tone === "done"
      ? "text-done"
      : tone === "danger"
        ? "text-danger"
        : "text-fg";
  return (
    <Card className="p-5">
      <p className="text-[13px] text-fg-secondary">{label}</p>
      <p className={`mt-1.5 text-[26px] font-semibold leading-none tracking-[-0.03em] tabular-nums ${color}`}>
        {value}
      </p>
    </Card>
  );
}

function ChartCard({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle>{title}</CardTitle>
          {subtitle ? <p className="mt-0.5 text-[12px] text-fg-muted">{subtitle}</p> : null}
        </div>
      </CardHeader>
      <CardContent className="pt-0">{children}</CardContent>
    </Card>
  );
}

function ChartEmpty() {
  return (
    <div className="grid h-[240px] place-items-center">
      <p className="text-[13px] text-fg-muted">暂无数据</p>
    </div>
  );
}
