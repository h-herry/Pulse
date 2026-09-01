"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { projectApi, teamApi, TEAM_KEY, PROJECT_KEY } from "@/lib/api";
import type { Project, Team } from "@/lib/types";

/**
 * 团队 / 项目上下文
 *
 * 信息架构关键点：团队与项目**不再编码进 URL**（原 /team/:id/project/:id/board），
 * 而是作为客户端上下文由顶部切换器驱动。这样既消除了「先选团队→再选项目→再进功能」
 * 的多级跳转，也让全站路由保持静态（支持 Next 静态导出打进 JAR）。
 */
interface ContextState {
  teamId: number | null;
  projectId: number | null;
  teams: Team[];
  projects: Project[];
  loadingTeams: boolean;
  loadingProjects: boolean;

  loadTeams: () => Promise<void>;
  selectTeam: (teamId: number) => Promise<void>;
  selectProject: (projectId: number) => void;
  refreshProjects: () => Promise<void>;
  createTeam: (name: string, description?: string) => Promise<Team>;
  createProject: (data: Partial<Project>) => Promise<Project>;
}

/** 同步到 localStorage，供 lib/api.ts 注入 X-Team-ID */
function syncTeam(teamId: number | null) {
  if (typeof window === "undefined") return;
  if (teamId) window.localStorage.setItem(TEAM_KEY, String(teamId));
  else window.localStorage.removeItem(TEAM_KEY);
}

function syncProject(projectId: number | null) {
  if (typeof window === "undefined") return;
  if (projectId) window.localStorage.setItem(PROJECT_KEY, String(projectId));
  else window.localStorage.removeItem(PROJECT_KEY);
}

export const useContextStore = create<ContextState>()(
  persist(
    (set, get) => ({
      teamId: null,
      projectId: null,
      teams: [],
      projects: [],
      loadingTeams: false,
      loadingProjects: false,

      loadTeams: async () => {
        set({ loadingTeams: true });
        try {
          const teams = await teamApi.list();
          set({ teams: teams ?? [], loadingTeams: false });
          // 尚未选择团队时，自动选中第一个（或恢复持久化值）
          const current = get().teamId;
          if (!current || !teams?.some((t) => t.id === current)) {
            const first = teams?.[0]?.id ?? null;
            if (first) await get().selectTeam(first);
          } else {
            syncTeam(current);
            await get().refreshProjects();
          }
        } catch {
          set({ loadingTeams: false });
        }
      },

      selectTeam: async (teamId: number) => {
        syncTeam(teamId);
        set({ teamId, projectId: null, projects: [] });
        await get().refreshProjects();
      },

      selectProject: (projectId: number) => {
        syncProject(projectId);
        set({ projectId });
      },

      refreshProjects: async () => {
        const { teamId } = get();
        if (!teamId) return;
        set({ loadingProjects: true });
        try {
          const projects = await projectApi.list(teamId);
          const list = projects ?? [];
          set({ projects: list, loadingProjects: false });
          const current = get().projectId;
          if (!current || !list.some((p) => p.id === current)) {
            const first = list[0]?.id ?? null;
            syncProject(first);
            set({ projectId: first });
          } else {
            syncProject(current);
          }
        } catch {
          set({ loadingProjects: false });
        }
      },

      createTeam: async (name, description) => {
        const team = await teamApi.create({ name, description });
        await get().loadTeams();
        if (team?.id) await get().selectTeam(team.id);
        return team;
      },

      createProject: async (data) => {
        const { teamId } = get();
        if (!teamId) throw new Error("请先选择团队");
        const project = await projectApi.create(teamId, data);
        await get().refreshProjects();
        if (project?.id) get().selectProject(project.id);
        return project;
      },
    }),
    {
      name: "pulse-context",
      partialize: (s) => ({ teamId: s.teamId, projectId: s.projectId }),
      onRehydrateStorage: () => (state) => {
        if (state?.teamId) syncTeam(state.teamId);
        if (state?.projectId) syncProject(state.projectId);
      },
    }
  )
);
