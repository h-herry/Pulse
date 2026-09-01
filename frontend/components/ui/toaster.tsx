"use client";

import { Toaster as SonnerToaster } from "sonner";
import { useThemeStore } from "@/store/theme";

/** 全局 Toast（苹果风：圆角、柔和阴影、右上角） */
export function Toaster() {
  const mode = useThemeStore((s) => s.mode);
  const dark =
    mode === "dark" ||
    (mode === "system" &&
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches);

  return (
    <SonnerToaster
      theme={dark ? "dark" : "light"}
      position="top-center"
      offset={72}
      toastOptions={{
        classNames: {
          toast:
            "!rounded-[var(--radius-md)] !border !border-hairline !bg-surface !text-fg !shadow-lg !text-[14px]",
          description: "!text-fg-secondary",
          actionButton: "!bg-accent !text-white !rounded-[var(--radius-sm)]",
          cancelButton: "!bg-surface-3 !text-fg-secondary !rounded-[var(--radius-sm)]",
        },
      }}
    />
  );
}
