"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import { authApi, TOKEN_KEY } from "@/lib/api";
import type { User } from "@/lib/types";

interface AuthState {
  token: string;
  user: User | null;
  hydrated: boolean;
  loading: boolean;
  /** 登录并拉取当前用户 */
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
  /** 用已有 token 拉取用户信息 */
  loadMe: () => Promise<void>;
  setToken: (token: string) => void;
  setUser: (user: User | null) => void;
}

/** 同步 token 到 localStorage，供 lib/api.ts 拦截器读取（避免 store↔api 循环依赖） */
function syncToken(token: string) {
  if (typeof window === "undefined") return;
  if (token) window.localStorage.setItem(TOKEN_KEY, token);
  else window.localStorage.removeItem(TOKEN_KEY);
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: "",
      user: null,
      hydrated: false,
      loading: false,

      setToken: (token) => {
        syncToken(token);
        set({ token });
      },

      setUser: (user) => set({ user }),

      login: async (email, password) => {
        set({ loading: true });
        try {
          const resp = await authApi.login(email, password);
          // LoginResponse: { token, userId, displayName, email }
          const token = resp?.token;
          if (!token) throw new Error("登录失败：未返回令牌");
          syncToken(token);
          set({ token, hydrated: true });
          const me = await authApi.me();
          set({ user: me, loading: false });
        } catch (e) {
          set({ loading: false });
          throw e;
        }
      },

      register: async (email, password, displayName) => {
        set({ loading: true });
        try {
          const resp = await authApi.register(email, password, displayName);
          const token = resp?.token;
          if (!token) throw new Error("注册失败：未返回令牌");
          syncToken(token);
          set({ token, hydrated: true });
          const me = await authApi.me();
          set({ user: me, loading: false });
        } catch (e) {
          set({ loading: false });
          throw e;
        }
      },

      logout: async () => {
        try {
          await authApi.logout();
        } catch {
          /* 忽略：本地清理优先 */
        }
        syncToken("");
        set({ token: "", user: null });
      },

      loadMe: async () => {
        const { token } = get();
        if (!token) {
          set({ user: null, hydrated: true });
          return;
        }
        syncToken(token);
        try {
          const me = await authApi.me();
          set({ user: me, hydrated: true });
        } catch {
          // token 失效（api 层已处理 401 跳转）
          syncToken("");
          set({ user: null, token: "", hydrated: true });
        }
      },
    }),
    {
      name: "pulse-auth",
      // 只持久化身份，不持久化 loading 等瞬时态
      partialize: (s) => ({ token: s.token, user: s.user }),
      onRehydrateStorage: () => (state) => {
        // 恢复后把 token 写回 TOKEN_KEY，保证 api 拦截器可立即使用
        if (state?.token) syncToken(state.token);
        if (state) state.hydrated = true;
      },
    }
  )
);
