"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/input";
import { Label } from "@/components/ui/input";
import { taskApi, teamApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useUiStore } from "@/store/ui";

const PRIORITIES = ["P0", "P1", "P2", "P3", "P4", "P5"];

/**
 * 快速新建任务
 *
 * 流程优化关键点：原 TaskCreateWizard 是「选项目 → 填详情 → 确认」多步向导，
 * 新建一张卡片要点 3～4 次。现在收敛为「一步内联表单」，默认带出当前项目，
 * Enter 即创建；需要更丰富字段时再进任务详情补充。
 */
export function QuickCreate() {
  const queryClient = useQueryClient();
  const { teamId, projectId, projects } = useContextStore();
  const { quickCreateOpen, setQuickCreateOpen, openTask } = useUiStore();

  const [title, setTitle] = React.useState("");
  const [targetProject, setTargetProject] = React.useState<number | null>(null);
  const [assigneeId, setAssigneeId] = React.useState<number | null>(null);
  const [priority, setPriority] = React.useState("P2");
  const inputRef = React.useRef<HTMLInputElement>(null);

  const pid = targetProject ?? projectId;

  const { data: members = [] } = useQuery({
    queryKey: ["members", teamId],
    queryFn: () => teamApi.members(teamId!),
    enabled: !!teamId,
    staleTime: 60_000,
  });

  const create = useMutation({
    mutationFn: async () => {
      if (!pid) throw new Error("请先选择项目");
      return taskApi.create(pid, {
        title: title.trim(),
        priority,
        assigneeId: assigneeId ?? undefined,
        status: "TODO",
      });
    },
    onSuccess: (task) => {
      toast.success("任务已创建");
      // 让看板/列表立即刷新
      void queryClient.invalidateQueries({ queryKey: ["board"] });
      void queryClient.invalidateQueries({ queryKey: ["tasks"] });
      setTitle("");
      setAssigneeId(null);
      setPriority("P2");
      setQuickCreateOpen(false);
      if (task?.id) openTask(task.id);
    },
    onError: (e: Error) => toast.error(e.message || "创建失败"),
  });

  // 打开时聚焦标题框
  React.useEffect(() => {
    if (quickCreateOpen) {
      const t = setTimeout(() => inputRef.current?.focus(), 60);
      return () => clearTimeout(t);
    }
  }, [quickCreateOpen]);

  const submit = () => {
    if (!title.trim() || create.isPending) return;
    create.mutate();
  };

  return (
    // 单例浮层：触发器分散在顶栏（桌面）与移动栏（移动端），但 Popover 只挂载一次，
    // 由 PopoverAnchor 固定在右上角定位，避免两处都渲染导致双浮层重叠。
    <Popover open={quickCreateOpen} onOpenChange={setQuickCreateOpen}>
      <PopoverAnchor asChild>
        <span className="fixed right-4 top-[60px] z-0 h-0 w-0" aria-hidden />
      </PopoverAnchor>

      <PopoverContent align="end" className="w-[calc(100vw-2rem)] max-w-[360px] p-4">
        <div className="mb-3 flex items-center gap-2">
          <Sparkles className="size-4 text-accent" />
          <h3 className="text-[14px] font-semibold text-fg">快速新建</h3>
        </div>

        <div className="space-y-3">
          <div>
            <Label htmlFor="qc-title">标题</Label>
            <Input
              id="qc-title"
              ref={inputRef}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") submit();
              }}
              placeholder="要做什么？"
              maxLength={200}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="qc-project">项目</Label>
              <Select
                id="qc-project"
                value={pid ?? ""}
                onChange={(e) => setTargetProject(Number(e.target.value) || null)}
              >
                {projects.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </Select>
            </div>

            <div>
              <Label htmlFor="qc-priority">优先级</Label>
              <Select
                id="qc-priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value)}
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          <div>
            <Label htmlFor="qc-assignee">负责人（可选）</Label>
            <Select
              id="qc-assignee"
              value={assigneeId ?? ""}
              onChange={(e) => setAssigneeId(Number(e.target.value) || null)}
            >
              <option value="">未分配</option>
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName ?? m.email ?? `用户 ${m.userId}`}
                </option>
              ))}
            </Select>
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between">
          <span className="text-[11px] text-fg-muted">Enter 直接创建</span>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" onClick={() => setQuickCreateOpen(false)}>
              取消
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={submit}
              disabled={!title.trim() || create.isPending}
            >
              {create.isPending ? <Loader2 className="size-3.5 animate-spin" /> : null}
              创建
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
