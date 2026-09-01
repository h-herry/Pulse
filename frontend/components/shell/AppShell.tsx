"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { useAuthStore } from "@/store/auth";
import { useContextStore } from "@/store/context";
import { Sidebar, MobileNav } from "./Sidebar";
import { TopBar, MobileTopBar } from "./TopBar";
import { CommandPalette } from "./CommandPalette";
import { QuickCreate } from "./QuickCreate";
import { TaskPanel } from "@/components/task/TaskPanel";
import { useBoardRealtime, useNotificationRealtime } from "@/lib/realtime";

/**
 * 应用外壳
 *
 * 职责：
 * 1. 鉴权守卫（无 token → /login）
 * 2. 初始化团队/项目上下文（顶部切换器的数据来源）
 * 3. 实时协作（看板变更 / 通知推送）
 * 4. 组装单层侧边导航 + 毛玻璃顶栏 + 命令面板 + 任务抽屉
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { token, user, loadMe, hydrated } = useAuthStore();
  const { loadTeams, teams } = useContextStore();
  const [ready, setReady] = React.useState(false);

  // 实时：看板变更与通知推送（内部自行处理未连接的降级）
  useBoardRealtime();
  useNotificationRealtime();

  // 鉴权守卫
  React.useEffect(() => {
    if (!hydrated) return;
    if (!token) {
      router.replace("/login");
      return;
    }
    if (!user) void loadMe();
  }, [hydrated, token, user, loadMe, router]);

  // 初始化上下文
  React.useEffect(() => {
    if (!token) return;
    void loadTeams().finally(() => setReady(true));
  }, [token, loadTeams]);

  // 未就绪时展示轻量骨架，避免白屏与布局跳动
  if (!hydrated || !token) {
    return (
      <div className="grid min-h-screen place-items-center bg-canvas">
        <Loader2 className="size-6 animate-spin text-fg-muted" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-canvas">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopBar />
        <MobileTopBar />

        <main
          className="flex-1 pb-20 lg:pb-0"
          style={{ minHeight: "calc(100dvh - var(--topbar-h))" }}
        >
          {teams.length === 0 && !ready ? (
            <div className="grid h-64 place-items-center">
              <Loader2 className="size-5 animate-spin text-fg-muted" />
            </div>
          ) : (
            children
          )}
        </main>
      </div>

      <MobileNav />
      <CommandPalette />
      <QuickCreate />
      <TaskPanel />
    </div>
  );
}
