"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Send, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { commentApi } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { UserAvatar } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { fromNow } from "@/lib/utils";
import type { TaskComment } from "@/lib/types";

export function CommentSection({ taskId }: { taskId: number }) {
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((s) => s.user);
  const [content, setContent] = React.useState("");

  const { data: comments = [], isLoading } = useQuery<TaskComment[]>({
    queryKey: ["comments", taskId],
    queryFn: () => commentApi.list(taskId, 1, 50),
    enabled: !!taskId,
  });

  const add = useMutation({
    mutationFn: () => commentApi.create(taskId, { content: content.trim() }),
    onSuccess: () => {
      setContent("");
      void queryClient.invalidateQueries({ queryKey: ["comments", taskId] });
    },
    onError: (e: Error) => toast.error(e.message || "评论失败"),
  });

  const remove = useMutation({
    mutationFn: (id: number) => commentApi.remove(taskId, id),
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["comments", taskId] }),
    onError: (e: Error) => toast.error(e.message || "删除失败"),
  });

  const submit = () => {
    if (!content.trim() || add.isPending) return;
    add.mutate();
  };

  return (
    <div className="pt-3">
      {/* 输入区 */}
      <div className="flex gap-2.5">
        <UserAvatar
          name={currentUser?.displayName ?? currentUser?.email}
          src={currentUser?.avatarUrl}
          size={28}
        />
        <div className="min-w-0 flex-1">
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) submit();
            }}
            placeholder="写下评论…（⌘ + Enter 发送）"
            className="min-h-[64px]"
          />
          <div className="mt-2 flex justify-end">
            <Button
              variant="primary"
              size="sm"
              onClick={submit}
              disabled={!content.trim() || add.isPending}
            >
              {add.isPending ? <Loader2 className="size-3.5 animate-spin" /> : <Send className="size-3.5" />}
              发送
            </Button>
          </div>
        </div>
      </div>

      {/* 评论列表 */}
      <div className="mt-5 space-y-4">
        {isLoading ? (
          Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))
        ) : comments.length === 0 ? (
          <EmptyState compact title="还没有评论" description="成为第一个留言的人" />
        ) : (
          comments.map((c) => (
            <div key={c.id} className="group flex gap-2.5">
              <UserAvatar name={c.displayName} src={c.avatarUrl} size={28} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-[13px] font-medium text-fg">
                    {c.displayName ?? "用户"}
                  </span>
                  <span className="text-[11px] text-fg-muted">{fromNow(c.createdAt)}</span>
                  {currentUser && c.userId === currentUser.id ? (
                    <button
                      type="button"
                      onClick={() => remove.mutate(c.id)}
                      className="ml-auto text-fg-muted opacity-0 transition-opacity hover:text-danger group-hover:opacity-100"
                      aria-label="删除评论"
                    >
                      <Trash2 className="size-3.5" />
                    </button>
                  ) : null}
                </div>
                <p className="mt-1 whitespace-pre-wrap break-words text-[13.5px] leading-relaxed text-fg-secondary">
                  {c.content}
                </p>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
