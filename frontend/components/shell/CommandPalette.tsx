"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Command } from "cmdk";
import {
  BarChart3,
  GanttChartSquare,
  KanbanSquare,
  LayoutDashboard,
  ListChecks,
  Moon,
  Plus,
  Repeat,
  Search,
  Settings,
  Shield,
  Sun,
  UserCog,
  Users,
} from "lucide-react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { taskApi } from "@/lib/api";
import { useUiStore } from "@/store/ui";
import { useContextStore } from "@/store/context";
import { useAuthStore } from "@/store/auth";
import { useThemeStore } from "@/store/theme";
import { cn } from "@/lib/utils";
import { Dialog, DialogPortal, DialogOverlay } from "@/components/ui/dialog";
import { PriorityDot } from "@/components/ui/badge";

/**
 * 命令面板（⌘K）
 *
 * 流程优化关键点：把「导航 + 搜索 + 切项目 + 建任务 + 换主题」全部收进一个
 * 键盘直达的入口，用户不必再在侧边栏与多个菜单间来回切换层级。
 */
export function CommandPalette() {
  const router = useRouter();
  const { commandOpen, setCommandOpen, setQuickCreateOpen, openTask } = useUiStore();
  const { teamId, projects, selectProject } = useContextStore();
  const isAdmin = useAuthStore((s) => !!s.user?.isAdmin);
  const { toggle } = useThemeStore();

  const [q, setQ] = React.useState("");

  // ⌘K / Ctrl+K 全局唤起
  React.useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "k" && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setCommandOpen(!useUiStore.getState().commandOpen);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [setCommandOpen]);

  /**
   * 开合统一入口：关闭时顺手清空搜索词。
   * （放在事件处理里而不是 useEffect 里，避免「渲染后 setState」造成的级联渲染。）
   */
  const handleOpenChange = (open: boolean) => {
    setCommandOpen(open);
    if (!open) setQ("");
  };

  const { data: tasks = [] } = useQuery({
    queryKey: ["task-search", q, teamId],
    queryFn: () => taskApi.search(q, teamId!, 8),
    enabled: !!teamId && q.trim().length >= 1,
    staleTime: 10_000,
  });

  const go = (href: string) => {
    setCommandOpen(false);
    router.push(href);
  };

  const pages = [
    { href: "/overview", label: "总览", icon: LayoutDashboard },
    { href: "/my-tasks", label: "我的任务", icon: ListChecks },
    { href: "/board", label: "看板", icon: KanbanSquare },
    { href: "/iterations", label: "迭代", icon: Repeat },
    { href: "/reports", label: "报表", icon: BarChart3 },
    { href: "/timeline", label: "时间线", icon: GanttChartSquare },
    { href: "/team", label: "团队", icon: Users },
    ...(isAdmin
      ? [
          { href: "/members", label: "成员", icon: UserCog },
          { href: "/admin", label: "管理", icon: Shield },
        ]
      : []),
    { href: "/settings", label: "设置", icon: Settings },
  ];

  return (
    <Dialog open={commandOpen} onOpenChange={handleOpenChange}>
      <DialogPortal>
        <DialogOverlay />
        <DialogPrimitive.Content
          className={cn(
            "fixed left-1/2 top-1/2 z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100vw-2rem)] max-w-xl",
            "-translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-[var(--radius-lg)]",
            "border border-hairline bg-surface shadow-lg outline-none",
            "data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95"
          )}
          aria-label="命令面板"
        >
          <Command
            shouldFilter
            className="flex min-h-0 flex-1 flex-col [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-[11px] [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-wide [&_[cmdk-group-heading]]:text-fg-muted"
          >
            <div className="flex items-center gap-2 border-b border-hairline px-3.5">
              <Search className="size-4 shrink-0 text-fg-muted" />
              <Command.Input
                value={q}
                onValueChange={setQ}
                placeholder="搜索任务、跳转页面、切换项目…"
                className="h-12 w-full bg-transparent text-[15px] text-fg outline-none placeholder:text-fg-muted"
              />
            </div>

            <Command.List className="min-h-0 flex-1 overflow-y-auto p-1.5">
              <Command.Empty className="py-10 text-center text-[13px] text-fg-muted">
                没有匹配的结果
              </Command.Empty>

              {/* 任务搜索结果 */}
              {q.trim().length >= 1 && tasks.length > 0 ? (
                <Command.Group heading="任务">
                  {tasks.map((t) => (
                    <Item
                      key={t.id}
                      onSelect={() => {
                        setCommandOpen(false);
                        openTask(t.id);
                      }}
                    >
                      <PriorityDot priority={t.priority} />
                      <span className="truncate">{t.title}</span>
                      {t.projectKey ? (
                        <span className="ml-auto shrink-0 text-[11px] tabular-nums text-fg-muted">
                          {t.projectKey}-{t.taskNumber}
                        </span>
                      ) : null}
                    </Item>
                  ))}
                </Command.Group>
              ) : null}

              <Command.Group heading="跳转">
                {pages.map((p) => {
                  const Icon = p.icon;
                  return (
                    <Item key={p.href} onSelect={() => go(p.href)}>
                      <Icon className="size-4 text-fg-muted" />
                      {p.label}
                    </Item>
                  );
                })}
              </Command.Group>

              <Command.Group heading="项目">
                {projects.map((p) => (
                  <Item
                    key={p.id}
                    onSelect={() => {
                      selectProject(p.id);
                      setCommandOpen(false);
                      router.push("/board");
                    }}
                  >
                    <KanbanSquare className="size-4 text-fg-muted" />
                    <span className="truncate">{p.name}</span>
                    <span className="ml-auto shrink-0 text-[11px] uppercase text-fg-muted">
                      {p.projectKey}
                    </span>
                  </Item>
                ))}
              </Command.Group>

              <Command.Group heading="操作">
                <Item
                  onSelect={() => {
                    setCommandOpen(false);
                    setQuickCreateOpen(true);
                  }}
                >
                  <Plus className="size-4 text-fg-muted" />
                  新建任务
                </Item>
                <Item onSelect={() => { toggle(); setCommandOpen(false); }}>
                  <Sun className="size-4 text-fg-muted dark:hidden" />
                  <Moon className="hidden size-4 text-fg-muted dark:block" />
                  切换外观
                </Item>
              </Command.Group>
            </Command.List>
          </Command>
        </DialogPrimitive.Content>
      </DialogPortal>
    </Dialog>
  );
}

function Item({
  children,
  onSelect,
}: {
  children: React.ReactNode;
  onSelect: () => void;
}) {
  return (
    <Command.Item
      onSelect={onSelect}
      className={cn(
        "flex cursor-pointer items-center gap-2.5 rounded-[var(--radius-sm)] px-2.5 py-2",
        "text-[13.5px] text-fg",
        "data-[selected=true]:bg-accent-soft data-[selected=true]:text-accent"
      )}
    >
      {children}
    </Command.Item>
  );
}
