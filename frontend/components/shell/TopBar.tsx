"use client";

import * as React from "react";
import { ChevronRight, Plus, Search } from "lucide-react";
import { useUiStore } from "@/store/ui";
import { useContextStore } from "@/store/context";
import { TeamSwitcher } from "./TeamSwitcher";
import { ProjectSwitcher } from "./ProjectSwitcher";
import { Button } from "@/components/ui/button";
import { NotificationBell } from "./NotificationBell";
import { UserMenu } from "./UserMenu";
import { MobileContextBar } from "./MobileContextBar";

/**
 * 顶部栏（毛玻璃常驻）
 *
 * 承载「团队 / 项目」上下文切换与全局入口。功能入口全部在左侧单层侧边栏，
 * 顶栏只负责**当前作用域**，两者职责分离，避免了原先「作用域与功能混在
 * 一棵嵌套树里」导致的层层下钻。
 */
export function TopBar() {
  const { setCommandOpen, setQuickCreateOpen } = useUiStore();
  const { teamId } = useContextStore();

  return (
    <header
      className="sticky top-0 z-30 flex h-[var(--topbar-h)] items-center gap-2 border-b border-hairline glass px-4 lg:px-6"
      role="banner"
    >
      {/* 全局搜索 / ⌘K 入口 */}
      <button
        type="button"
        onClick={() => setCommandOpen(true)}
        className="group hidden h-8 w-9 items-center gap-2 rounded-[var(--radius-md)] bg-surface-2 px-2.5 text-fg-muted transition-all duration-200 hover:bg-surface-3 hover:w-[220px] focus-visible:outline-none focus-visible:shadow-[var(--shadow-focus)] md:flex sm:w-[200px] lg:w-[240px]"
        aria-label="搜索或执行命令"
      >
        <Search className="size-3.5 shrink-0" />
        <span className="hidden text-[13px] sm:inline">搜索任务…</span>
        <kbd className="ml-auto hidden shrink-0 rounded bg-surface-3 px-1.5 py-0.5 font-sans text-[10px] text-fg-muted sm:inline">
          ⌘K
        </kbd>
      </button>

      {/* 上下文面包屑：团队 / 项目（桌面） */}
      {teamId ? (
        <div className="ml-1 hidden items-center gap-0.5 md:flex">
          <TeamSwitcher />
          <ChevronRight className="size-3.5 shrink-0 text-fg-muted" />
          <ProjectSwitcher />
        </div>
      ) : null}

      <div className="ml-auto flex items-center gap-1.5">
        <Button
          variant="primary"
          size="sm"
          className="hidden gap-1.5 md:inline-flex"
          onClick={() => setQuickCreateOpen(true)}
          aria-label="新建任务"
        >
          <Plus className="size-4" />
          <span className="hidden sm:inline">新建任务</span>
        </Button>
        <NotificationBell />
        <UserMenu />
      </div>
    </header>
  );
}

/** 移动端：上下文切换独立成一行（顶栏空间不足） */
export function MobileTopBar() {
  const { setCommandOpen, setQuickCreateOpen } = useUiStore();

  return (
    <div className="md:hidden">
      <div className="flex items-center gap-2 px-4 pb-2.5">
        <button
          type="button"
          onClick={() => setCommandOpen(true)}
          className="flex h-8 flex-1 items-center gap-2 rounded-[var(--radius-md)] bg-surface-2 px-2.5 text-fg-muted"
        >
          <Search className="size-3.5" />
          <span className="text-[13px]">搜索任务…</span>
        </button>
        <Button
          variant="primary"
          size="sm"
          className="gap-1.5"
          onClick={() => setQuickCreateOpen(true)}
          aria-label="新建任务"
        >
          <Plus className="size-4" />
          <span className="hidden sm:inline">新建任务</span>
        </Button>
      </div>
      <MobileContextBar />
    </div>
  );
}
