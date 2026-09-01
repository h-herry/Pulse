"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { LogOut, Moon, Settings, Sun, UserRound } from "lucide-react";
import { toast } from "sonner";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { UserAvatar } from "@/components/ui/avatar";
import { useAuthStore } from "@/store/auth";
import { useThemeStore } from "@/store/theme";

/**
 * 用户菜单（扁平）
 * 原实现里「个人资料 / AI 配置 / 修改密码」散落在头像菜单的多个 Modal 里，
 * 现在统一收敛到 /settings 页，菜单只保留入口 + 外观 + 退出。
 */
export function UserMenu() {
  const router = useRouter();
  const { user, logout } = useAuthStore();
  const { toggle, mode } = useThemeStore();

  const dark =
    mode === "dark" ||
    (mode === "system" &&
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches);

  const onLogout = async () => {
    await logout();
    toast.success("已退出登录");
    router.push("/login");
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="rounded-full transition-transform duration-200 hover:scale-105 focus-visible:outline-none focus-visible:shadow-[var(--shadow-focus)]"
          aria-label="账户菜单"
        >
          <UserAvatar name={user?.displayName ?? user?.email} src={user?.avatarUrl} size={28} />
        </button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          <span className="block truncate normal-case tracking-normal text-fg">
            {user?.displayName ?? "用户"}
          </span>
          <span className="block truncate normal-case tracking-normal text-fg-muted">
            {user?.email}
          </span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />

        <DropdownMenuItem onSelect={() => router.push("/settings")}>
          <Settings />
          设置
        </DropdownMenuItem>
        <DropdownMenuItem
          onSelect={(e) => {
            e.preventDefault();
            toggle();
          }}
        >
          {dark ? <Sun /> : <Moon />}
          {dark ? "浅色模式" : "深色模式"}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => router.push("/my-tasks")}>
          <UserRound />
          我的任务
        </DropdownMenuItem>

        <DropdownMenuSeparator />
        <DropdownMenuItem destructive onSelect={() => void onLogout()}>
          <LogOut />
          退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
