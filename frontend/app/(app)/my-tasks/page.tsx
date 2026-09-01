"use client";

import * as React from "react";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { PageShell } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input, Select } from "@/components/ui/input";
import { SkeletonList } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, NoResults, ContextRequired } from "@/components/ui/empty-state";
import { TaskListItem } from "@/components/task/TaskListItem";
import { taskApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";

const TABS = [
  { value: "all", label: "全部" },
  { value: "TODO", label: "待办" },
  { value: "IN_PROGRESS", label: "进行中" },
  { value: "REVIEW", label: "审查中" },
  { value: "DONE", label: "已完成" },
];

/**
 * 我的任务（跨团队聚合）
 * 筛选控件全部平铺在一行，不做折叠二级菜单。
 */
export default function MyTasksPage() {
  const { teamId } = useContextStore();
  const { openTask } = useUiStore();
  const [tab, setTab] = React.useState("all");
  const [priority, setPriority] = React.useState("");
  const [kw, setKw] = React.useState("");

  const { data: tasks = [], isLoading, isError, refetch } = useQuery({
    queryKey: ["my-tasks"],
    queryFn: () => taskApi.my(),
    staleTime: 30_000,
  });

  const filtered = React.useMemo(() => {
    const keyword = kw.trim().toLowerCase();
    return tasks.filter((t) => {
      if (tab !== "all" && t.status !== tab) return false;
      if (priority && t.priority !== priority) return false;
      if (keyword && !t.title.toLowerCase().includes(keyword)) return false;
      return true;
    });
  }, [tasks, tab, priority, kw]);

  const countOf = (status: string) =>
    status === "all" ? tasks.length : tasks.filter((t) => t.status === status).length;

  if (!teamId) {
    return (
      <PageShell title="我的任务">
        <ContextRequired what="团队" />
      </PageShell>
    );
  }

  return (
    <PageShell title="我的任务" subtitle="跨团队聚合指派给你的所有任务">
      {/* 筛选行：一行平铺 */}
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <div className="min-w-0 flex-1 overflow-x-auto">
          <Tabs value={tab} onValueChange={setTab}>
            <TabsList className="w-max">
              {TABS.map((t) => (
                <TabsTrigger key={t.value} value={t.value}>
                  {t.label}
                  <span className="ml-1 text-[11px] tabular-nums text-fg-muted">
                    {countOf(t.value)}
                  </span>
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>

        <div className="relative ml-auto w-40">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-fg-muted" />
          <Input
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            placeholder="搜索标题…"
            className="pl-8"
            aria-label="搜索任务"
          />
        </div>

        <div className="w-28">
          <Select
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            aria-label="按优先级筛选"
          >
            <option value="">全部优先级</option>
            {["P0", "P1", "P2", "P3", "P4", "P5"].map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </Select>
        </div>
      </div>

      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonList rows={6} />
          </div>
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : filtered.length === 0 ? (
          kw.trim() ? (
            <NoResults keyword={kw} />
          ) : (
            <EmptyState compact title="这里空空如也" description="没有符合当前筛选条件的任务" />
          )
        ) : (
          <div>
            {filtered.map((t) => (
              <TaskListItem key={t.id} task={t} onOpen={openTask} showProject />
            ))}
          </div>
        )}
      </Card>
    </PageShell>
  );
}
