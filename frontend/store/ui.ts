"use client";

import { create } from "zustand";

interface UiState {
  /** ⌘K 命令面板 */
  commandOpen: boolean;
  /** 顶栏「+」快速新建任务 */
  quickCreateOpen: boolean;
  /** 任务详情抽屉（SlideOver）：选中的任务 ID */
  activeTaskId: number | null;
  /** 侧边栏折叠（桌面） */
  sidebarCollapsed: boolean;

  setCommandOpen: (v: boolean) => void;
  setQuickCreateOpen: (v: boolean) => void;
  openTask: (id: number) => void;
  closeTask: () => void;
  toggleSidebar: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  commandOpen: false,
  quickCreateOpen: false,
  activeTaskId: null,
  sidebarCollapsed: false,

  setCommandOpen: (commandOpen) => set({ commandOpen }),
  setQuickCreateOpen: (quickCreateOpen) => set({ quickCreateOpen }),
  openTask: (activeTaskId) => set({ activeTaskId }),
  closeTask: () => set({ activeTaskId: null }),
  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
}));
