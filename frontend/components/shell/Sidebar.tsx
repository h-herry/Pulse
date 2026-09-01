"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BarChart3,
  GanttChartSquare,
  KanbanSquare,
  LayoutDashboard,
  ListChecks,
  Repeat,
  Shield,
  UserCog,
  Users,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/store/auth";
import { PulseLogo } from "./PulseLogo";

/**
 * 单层侧边导航
 *
 * IA 关键点：**不再有任何分组嵌套**。原实现是「工作区 → 团队 → 项目 → 视图」四级，
 * 用户必须先选中团队、再选中项目才能进入功能。现在所有入口平铺为一级，
 * 团队/项目由顶部切换器承载——进入任何功能都不再有前置跳转。
 */
interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  adminOnly?: boolean;
  /** 需要项目上下文 */
  needsProject?: boolean;
}

export const NAV_ITEMS: NavItem[] = [
  { href: "/overview", label: "总览", icon: LayoutDashboard },
  { href: "/my-tasks", label: "我的任务", icon: ListChecks },
  { href: "/board", label: "看板", icon: KanbanSquare, needsProject: true },
  { href: "/iterations", label: "迭代", icon: Repeat, needsProject: true },
  { href: "/reports", label: "报表", icon: BarChart3, needsProject: true },
  { href: "/timeline", label: "时间线", icon: GanttChartSquare, needsProject: true },
  { href: "/team", label: "团队", icon: Users },
  { href: "/members", label: "成员", icon: UserCog, adminOnly: true },
  { href: "/admin", label: "管理", icon: Shield, adminOnly: true },
];

/** 移动端底部 Tab 只保留最高频的 4 个 + 团队 */
export const MOBILE_NAV = [
  { href: "/overview", label: "总览", icon: LayoutDashboard },
  { href: "/board", label: "看板", icon: KanbanSquare },
  { href: "/my-tasks", label: "我的任务", icon: ListChecks },
  { href: "/team", label: "团队", icon: Users },
];

export function Sidebar() {
  const pathname = usePathname();
  const isAdmin = useAuthStore((s) => !!s.user?.isAdmin);

  return (
    <aside
      className={cn(
        "sticky top-0 z-20 hidden h-[100dvh] shrink-0 self-start flex-col border-r border-hairline lg:flex",
        "glass w-[var(--sidebar-w)]"
      )}
      aria-label="主导航"
    >
      <div className="flex h-[var(--topbar-h)] items-center gap-2.5 px-5">
        <PulseLogo className="size-7" />
        <span className="text-[15px] font-semibold tracking-[-0.02em]">Pulse 脉搏</span>
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto px-3 py-3">
        {NAV_ITEMS.filter((i) => !i.adminOnly || isAdmin).map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={cn(
                "group flex items-center gap-2.5 rounded-[var(--radius-sm)] px-3 py-2",
                "text-[13.5px] transition-all duration-200 easing-apple",
                active
                  ? "bg-accent-soft font-medium text-accent"
                  : "text-fg-secondary hover:bg-surface-2 hover:text-fg"
              )}
            >
              <Icon
                className={cn(
                  "size-4 shrink-0 transition-colors",
                  active ? "text-accent" : "text-fg-muted group-hover:text-fg-secondary"
                )}
              />
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="px-3 pb-4 pt-2">
        <p className="px-3 text-[11px] leading-relaxed text-fg-muted">
          按 <kbd className="rounded bg-surface-3 px-1 py-0.5 font-sans text-[10px]">⌘K</kbd>{" "}
          唤起命令面板
        </p>
      </div>
    </aside>
  );
}

/** 移动端底部导航 */
export function MobileNav() {
  const pathname = usePathname();
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-40 border-t border-hairline glass lg:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
      aria-label="底部导航"
    >
      <div className="flex items-stretch">
        {MOBILE_NAV.map((item) => {
          const active = pathname === item.href;
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex flex-1 flex-col items-center gap-1 py-2 text-[11px] transition-colors",
                active ? "text-accent" : "text-fg-muted"
              )}
              aria-current={active ? "page" : undefined}
            >
              <Icon className="size-5" />
              {item.label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
