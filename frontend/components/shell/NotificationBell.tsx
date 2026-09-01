"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { toast } from "sonner";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { notificationApi } from "@/lib/api";
import { cn, fromNow } from "@/lib/utils";
import type { Notification } from "@/lib/types";
import { EmptyState } from "@/components/ui/empty-state";

/**
 * 通知中心
 * 单层 popover 直达列表（原实现是铃铛下拉再点「查看全部」的两级跳转）。
 */
export function NotificationBell() {
  const queryClient = useQueryClient();
  const [open, setOpen] = React.useState(false);

  const { data: list = [], isLoading } = useQuery<Notification[]>({
    queryKey: ["notifications"],
    queryFn: () => notificationApi.list(),
    enabled: open,
    staleTime: 15_000,
  });

  const { data: unread = 0 } = useQuery<number>({
    queryKey: ["notifications", "unread"],
    queryFn: () => notificationApi.unreadCount(),
    refetchInterval: 60_000,
  });

  const readOne = useMutation({
    mutationFn: (id: number) => notificationApi.read(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const readAll = useMutation({
    mutationFn: () => notificationApi.readAll(),
    onSuccess: () => {
      toast.success("已全部标记为已读");
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="iconSm" className="relative" aria-label="通知">
          <Bell className="size-4" />
          {unread > 0 ? (
            <span
              className={cn(
                "absolute -right-0.5 -top-0.5 grid min-w-[16px] place-items-center",
                "rounded-full bg-danger px-1 text-[10px] font-semibold leading-4 text-white"
              )}
            >
              {unread > 99 ? "99+" : unread}
            </span>
          ) : null}
        </Button>
      </PopoverTrigger>

      <PopoverContent align="end" className="w-[380px] p-0">
        <div className="flex items-center justify-between border-b border-hairline px-4 py-3">
          <h3 className="text-[14px] font-semibold text-fg">通知</h3>
          {list.length > 0 ? (
            <button
              type="button"
              onClick={() => readAll.mutate()}
              className="flex items-center gap-1 text-[12px] text-accent hover:underline"
            >
              <CheckCheck className="size-3.5" />
              全部已读
            </button>
          ) : null}
        </div>

        <div className="max-h-[420px] overflow-y-auto">
          {isLoading ? (
            <div className="space-y-3 p-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="shimmer h-14 w-full rounded-[var(--radius-sm)]" />
              ))}
            </div>
          ) : list.length === 0 ? (
            <EmptyState compact title="暂无通知" description="有新的动态会在这里出现" />
          ) : (
            list.map((n) => (
              <button
                key={n.id}
                type="button"
                onClick={() => {
                  if (!n.isRead) readOne.mutate(n.id);
                }}
                className={cn(
                  "flex w-full flex-col gap-1 border-b border-hairline px-4 py-3 text-left",
                  "transition-colors hover:bg-surface-2",
                  !n.isRead && "bg-accent-soft/40"
                )}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="text-[13.5px] font-medium text-fg">{n.title}</span>
                  <span className="shrink-0 text-[11px] text-fg-muted">
                    {fromNow(n.createdAt)}
                  </span>
                </div>
                {n.body ? (
                  <span className="line-clamp-2 text-[12.5px] leading-relaxed text-fg-secondary">
                    {n.body}
                  </span>
                ) : null}
              </button>
            ))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
