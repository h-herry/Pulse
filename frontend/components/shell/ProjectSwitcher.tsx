"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Check, ChevronsUpDown, Plus, Search } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { useContextStore } from "@/store/context";
import { cn } from "@/lib/utils";

/**
 * 项目切换器
 * 与团队切换器并列于顶栏，构成「团队 / 项目」二级上下文，
 * 但**只影响数据范围，不改变路由层级**——功能入口始终平铺在侧边栏。
 */
export function ProjectSwitcher() {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const [q, setQ] = React.useState("");
  const { projects, projectId, selectProject, loadingProjects, teamId } =
    useContextStore();
  const current = projects.find((p) => p.id === projectId);

  const filtered = React.useMemo(() => {
    const kw = q.trim().toLowerCase();
    if (!kw) return projects;
    return projects.filter(
      (p) =>
        p.name.toLowerCase().includes(kw) ||
        (p.projectKey ?? "").toLowerCase().includes(kw)
    );
  }, [projects, q]);

  if (!teamId) return null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="max-w-[200px] gap-1.5 px-2"
          aria-label="切换项目"
        >
          <span className="truncate font-medium text-fg">
            {loadingProjects ? "加载中…" : (current?.name ?? "选择项目")}
          </span>
          <ChevronsUpDown className="size-3.5 text-fg-muted" />
        </Button>
      </PopoverTrigger>

      <PopoverContent className="w-64 p-1.5">
        <div className="relative mb-1.5">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-fg-muted" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="搜索项目…"
            className="h-8 w-full rounded-[var(--radius-sm)] bg-surface-2 pl-8 pr-2.5 text-[13px] text-fg placeholder:text-fg-muted focus:bg-surface focus:outline-none focus:shadow-[var(--shadow-focus)]"
          />
        </div>

        <div className="max-h-[280px] overflow-y-auto">
          {filtered.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => {
                selectProject(p.id);
                setOpen(false);
              }}
              className={cn(
                "flex w-full items-center justify-between gap-2 rounded-[var(--radius-sm)] px-2.5 py-1.5",
                "text-[13px] transition-colors hover:bg-surface-2",
                p.id === projectId ? "font-medium text-accent" : "text-fg"
              )}
            >
              <span className="truncate">{p.name}</span>
              {p.id === projectId ? <Check className="size-3.5 shrink-0" /> : null}
            </button>
          ))}
          {filtered.length === 0 ? (
            <p className="px-2.5 py-3 text-[13px] text-fg-muted">未找到项目</p>
          ) : null}
        </div>

        <div className="mt-1 border-t border-hairline pt-1">
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              router.push("/team");
            }}
            className="flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-2.5 py-1.5 text-[13px] text-fg transition-colors hover:bg-surface-2"
          >
            <Plus className="size-3.5" />
            新建项目
          </button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
