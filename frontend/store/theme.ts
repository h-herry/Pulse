"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";

export type ThemeMode = "light" | "dark" | "system";

interface ThemeState {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
  /** 把 mode 应用到 <html> 的 class（dark 策略） */
  apply: () => void;
}

function resolveDark(mode: ThemeMode) {
  if (mode === "system") {
    if (typeof window === "undefined") return false;
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
  }
  return mode === "dark";
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      mode: "light", // 苹果纯白为默认主题
      setMode: (mode) => {
        set({ mode });
        get().apply();
      },
      toggle: () => {
        const next = resolveDark(get().mode) ? "light" : "dark";
        set({ mode: next });
        get().apply();
      },
      apply: () => {
        if (typeof document === "undefined") return;
        const dark = resolveDark(get().mode);
        document.documentElement.classList.toggle("dark", dark);
        document.documentElement.style.colorScheme = dark ? "dark" : "light";
      },
    }),
    { name: "pulse-theme" }
  )
);
