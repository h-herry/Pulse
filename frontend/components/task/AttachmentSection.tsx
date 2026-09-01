"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, Loader2, Paperclip, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { attachmentApi } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { humanSize, fromNow } from "@/lib/utils";
import type { TaskAttachment } from "@/lib/types";

export function AttachmentSection({ taskId }: { taskId: number }) {
  const queryClient = useQueryClient();
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = React.useState(false);

  const { data: files = [], isLoading } = useQuery<TaskAttachment[]>({
    queryKey: ["attachments", taskId],
    queryFn: () => attachmentApi.list(taskId),
    enabled: !!taskId,
  });

  const remove = useMutation({
    mutationFn: (id: number) => attachmentApi.remove(taskId, id),
    onSuccess: () => {
      toast.success("附件已删除");
      void queryClient.invalidateQueries({ queryKey: ["attachments", taskId] });
    },
    onError: (e: Error) => toast.error(e.message || "删除失败"),
  });

  const onPick = async (file: File) => {
    setUploading(true);
    try {
      await attachmentApi.upload(taskId, file);
      toast.success("上传成功");
      void queryClient.invalidateQueries({ queryKey: ["attachments", taskId] });
    } catch (e) {
      toast.error((e as Error).message || "上传失败");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <div className="pt-3">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-[12px] text-fg-muted">共 {files.length} 个附件</span>
        <Button variant="secondary" size="sm" onClick={() => inputRef.current?.click()}>
          {uploading ? <Loader2 className="size-3.5 animate-spin" /> : <Upload className="size-3.5" />}
          上传
        </Button>
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) void onPick(f);
          }}
        />
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {Array.from({ length: 2 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : files.length === 0 ? (
        <EmptyState
          compact
          icon={<Paperclip className="size-5" />}
          title="暂无附件"
          description="上传设计稿、文档或截图"
        />
      ) : (
        <ul className="space-y-2">
          {files.map((f) => (
            <li
              key={f.id}
              className="group flex items-center gap-3 rounded-[var(--radius-md)] border border-hairline bg-surface-2 px-3 py-2.5"
            >
              <Paperclip className="size-4 shrink-0 text-fg-muted" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-[13px] text-fg">{f.fileName}</p>
                <p className="text-[11px] text-fg-muted">
                  {humanSize(f.fileSize)} · {fromNow(f.createdAt)}
                </p>
              </div>
              <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                <a
                  href={attachmentApi.downloadUrl(taskId, f.id)}
                  target="_blank"
                  rel="noreferrer"
                  className="grid size-7 place-items-center rounded-[var(--radius-sm)] text-fg-muted hover:bg-surface-3 hover:text-fg"
                  aria-label="下载附件"
                >
                  <Download className="size-3.5" />
                </a>
                <button
                  type="button"
                  onClick={() => remove.mutate(f.id)}
                  className="grid size-7 place-items-center rounded-[var(--radius-sm)] text-fg-muted hover:bg-danger-soft hover:text-danger"
                  aria-label="删除附件"
                >
                  <Trash2 className="size-3.5" />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
