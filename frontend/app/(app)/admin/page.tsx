"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Search, Shield, Snowflake, Pencil, UserCog } from "lucide-react";
import { toast } from "sonner";
import { PageShell } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input, Select, Label, Field } from "@/components/ui/input";
import { Badge, StatusBadge, PriorityDot } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { UserAvatar } from "@/components/ui/avatar";
import { SkeletonList } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from "@/components/ui/dialog";
import { adminApi } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { useUiStore } from "@/store/ui";
import { fromNow, cn } from "@/lib/utils";
import type { AuditLog, Task, User } from "@/lib/types";

/** 系统角色选项（全局维度：用户 / 管理员；组长属团队内角色，不在此列） */
const ROLE_OPTIONS = [
  { value: "user", label: "用户" },
  { value: "admin", label: "管理员" },
] as const;

type SystemRole = (typeof ROLE_OPTIONS)[number]["value"];

/**
 * 管理后台（系统管理员）
 * 用户 / 审计日志 / 上帝视角任务，用标签页平级组织，不做嵌套菜单。
 */
export default function AdminPage() {
  const isAdmin = useAuthStore((s) => !!s.user?.isAdmin);

  if (!isAdmin) {
    return (
      <PageShell title="管理后台">
        <EmptyState
          icon={<Shield className="size-6" />}
          title="无权访问"
          description="仅系统管理员可进入管理后台"
        />
      </PageShell>
    );
  }

  return (
    <PageShell title="管理后台" subtitle="用户、审计与全局任务视图">
      <Tabs defaultValue="users">
        <TabsList>
          <TabsTrigger value="users">用户</TabsTrigger>
          <TabsTrigger value="tasks">全部任务</TabsTrigger>
          <TabsTrigger value="audit">审计日志</TabsTrigger>
        </TabsList>

        <TabsContent value="users">
          <UsersTab />
        </TabsContent>
        <TabsContent value="tasks">
          <AllTasksTab />
        </TabsContent>
        <TabsContent value="audit">
          <AuditTab />
        </TabsContent>
      </Tabs>
    </PageShell>
  );
}

/* ---------- 用户 ---------- */

function UsersTab() {
  const queryClient = useQueryClient();
  const [kw, setKw] = React.useState("");
  /** 编辑弹窗状态 */
  const [editUser, setEditUser] = React.useState<User | null>(null);
  const [editName, setEditName] = React.useState("");
  const [editAvatar, setEditAvatar] = React.useState("");

  const {
    data: users = [],
    isLoading,
    isError,
    refetch,
  } = useQuery<User[]>({
    queryKey: ["admin-users"],
    queryFn: () => adminApi.users(),
  });

  /* ---- mutations ---- */

  const roleMutation = useMutation({
    mutationFn: ({ id, role }: { id: number; role: SystemRole }) =>
      adminApi.updateUserRole(id, role),
    onSuccess: () => {
      toast.success("角色已更新");
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (e: Error) => toast.error(e.message || "角色更新失败"),
  });

  const freezeMutation = useMutation({
    mutationFn: (args: { id: number; enabled: boolean }) =>
      adminApi.toggleUserEnabled(args.id, args.enabled),
    onSuccess: (_, vars) => {
      toast.success(vars.enabled ? "用户已解冻" : "用户已冻结");
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (e: Error) => toast.error(e.message || "操作失败"),
  });

  const reset = useMutation({
    mutationFn: (id: number) => adminApi.resetPassword(id),
    onSuccess: () => toast.success("密码已重置为默认密码"),
    onError: (e: Error) => toast.error(e.message || "重置失败"),
  });

  const updateProfile = useMutation({
    mutationFn: ({ id, displayName }: { id: number; displayName: string }) =>
      adminApi.updateUserProfile(id, displayName),
    onSuccess: () => {
      toast.success("资料已更新");
      setEditUser(null);
      void queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (e: Error) => toast.error(e.message || "更新失败"),
  });

  /* ---- derived ---- */

  const filtered = users.filter((u) => {
    const keyword = kw.trim().toLowerCase();
    if (!keyword) return true;
    return (
      u.displayName.toLowerCase().includes(keyword) ||
      u.email.toLowerCase().includes(keyword)
    );
  });

  /** 推断当前角色（后端 is_system_admin 映射为 admin / user） */
  const resolveRole = (u: User): SystemRole => {
    if (u.role) return u.role as SystemRole;
    return u.isAdmin ? "admin" : "user";
  };

  /** 打开编辑弹窗 */
  const openEdit = (u: User) => {
    setEditUser(u);
    setEditName(u.displayName);
    setEditAvatar(u.avatarUrl ?? "");
  };

  /* ---- render ---- */

  return (
    <Card className="overflow-hidden">
      {/* 搜索栏 */}
      <div className="border-b border-hairline p-3">
        <div className="relative w-56">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-fg-muted" />
          <Input
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            placeholder="搜索用户…"
            className="pl-8"
            aria-label="搜索用户"
          />
        </div>
      </div>

      {/* 列表 */}
      {isLoading ? (
        <div className="p-4">
          <SkeletonList rows={5} />
        </div>
      ) : isError ? (
        <ErrorState onRetry={() => void refetch()} />
      ) : filtered.length === 0 ? (
        <EmptyState compact title="未找到用户" />
      ) : (
        <ul className="divide-y divide-hairline">
          {filtered.map((u) => {
            const role = resolveRole(u);
            const isFrozen = u.enabled === false;

            return (
              <li
                key={u.id}
                className={cn(
                  "flex flex-wrap items-center gap-2.5 px-4 py-3",
                  isFrozen && "opacity-50"
                )}
              >
                {/* 头像 + 名称 + 邮箱 */}
                <UserAvatar name={u.displayName} src={u.avatarUrl} size={32} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13.5px] font-medium text-fg">
                    {u.displayName}
                    {isFrozen && (
                      <Badge tone="danger" size="sm" className="ml-1.5">
                        已冻结
                      </Badge>
                    )}
                  </p>
                  <p className="truncate text-[12px] text-fg-muted">{u.email}</p>
                </div>

                {/* 操作区 */}
                <div className="flex shrink-0 items-center gap-1.5">
                  {/* 编辑 */}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => openEdit(u)}
                    aria-label={`编辑 ${u.displayName} 的资料`}
                    title="编辑资料"
                  >
                    <Pencil className="size-3.5" />
                  </Button>

                  {/* 重置密码 */}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => reset.mutate(u.id)}
                    aria-label={`重置 ${u.displayName} 的密码`}
                    title="重置密码"
                  >
                    <KeyRound className="size-3.5" />
                    <span className="hidden lg:inline">重置密码</span>
                  </Button>

                  {/* 冻结 / 解冻 */}
                  <Button
                    variant={isFrozen ? "primary" : "ghost"}
                    size="sm"
                    onClick={() =>
                      freezeMutation.mutate({ id: u.id, enabled: !!isFrozen })
                    }
                    aria-label={isFrozen ? `解冻 ${u.displayName}` : `冻结 ${u.displayName}`}
                    title={isFrozen ? "解冻用户" : "冻结用户"}
                  >
                    <Snowflake
                      className={cn(
                        "size-3.5",
                        isFrozen && "fill-current"
                      )}
                    />
                    <span className="hidden lg:inline">
                      {isFrozen ? "解冻" : "冻结"}
                    </span>
                  </Button>

                  {/* 角色下拉（用户 / 管理员） */}
                  <Select
                    value={role}
                    onChange={(e) =>
                      roleMutation.mutate({
                        id: u.id,
                        role: e.target.value as SystemRole,
                      })
                    }
                    aria-label={`设置 ${u.displayName} 的角色`}
                    className="min-w-[100px]"
                  >
                    {ROLE_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </Select>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {/* 编辑用户资料弹窗 */}
      <Dialog
        open={!!editUser}
        onOpenChange={(open) => !open && setEditUser(null)}
      >
        <DialogContent className="sm:max-w-[480px]">
          <DialogTitle className="flex items-center gap-2">
            <UserCog className="size-4.5" />
            个人资料
          </DialogTitle>

          {editUser && (
            <div className="space-y-4 py-1">
              <Field label="邮箱" hint="邮箱作为登录账号，暂不支持修改">
                <Input value={editUser.email} disabled className="bg-surface-2" />
              </Field>

              <Field label="显示名称">
                <Input
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  placeholder="输入显示名称"
                />
              </Field>

              <Field label="头像地址" hint="留空则使用姓名首字母母生成的头像">
                <Input
                  value={editAvatar}
                  onChange={(e) => setEditAvatar(e.target.value)}
                  placeholder="https://..."
                />
              </Field>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-4 border-t border-hairline">
            <Button
              variant="ghost"
              onClick={() => setEditUser(null)}
            >
              取消
            </Button>
            <Button
              variant="primary"
              disabled={!editName.trim() || updateProfile.isPending}
              onClick={() =>
                editUser &&
                updateProfile.mutate({
                  id: editUser.id,
                  displayName: editName.trim(),
                })
              }
            >
              {updateProfile.isPending ? "保存中…" : "保存"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

/* ---------- 全部任务（上帝视角） ---------- */

function AllTasksTab() {
  const { openTask } = useUiStore();
  const [kw, setKw] = React.useState("");
  const [status, setStatus] = React.useState("");

  const params = React.useMemo(
    () => ({ keyword: kw.trim() || undefined, status: status || undefined, size: 100 }),
    [kw, status]
  );

  const {
    data: tasks = [],
    isLoading,
    isError,
    refetch,
  } = useQuery<Task[]>({
    queryKey: ["admin-tasks", params],
    queryFn: () => adminApi.allTasks(params),
  });

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-center gap-2 border-b border-hairline p-3">
        <div className="relative w-56">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-fg-muted" />
          <Input
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            placeholder="搜索任务…"
            className="pl-8"
            aria-label="搜索任务"
          />
        </div>
        <div className="w-36">
          <Select value={status} onChange={(e) => setStatus(e.target.value)} aria-label="按状态筛选">
            <option value="">全部状态</option>
            <option value="TODO">待办</option>
            <option value="IN_PROGRESS">进行中</option>
            <option value="REVIEW">审查中</option>
            <option value="DONE">已完成</option>
          </Select>
        </div>
        <span className="ml-auto text-[12px] text-fg-muted">{tasks.length} 条</span>
      </div>

      {isLoading ? (
        <div className="p-4">
          <SkeletonList rows={6} />
        </div>
      ) : isError ? (
        <ErrorState onRetry={() => void refetch()} />
      ) : tasks.length === 0 ? (
        <EmptyState compact title="没有符合条件的任务" />
      ) : (
        <ul className="divide-y divide-hairline">
          {tasks.map((t) => (
            <li key={t.id}>
              <button
                type="button"
                onClick={() => openTask(t.id)}
                className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-2"
              >
                <PriorityDot priority={t.priority} />
                <span className="min-w-0 flex-1 truncate text-[13.5px] font-medium text-fg">
                  {t.title}
                </span>
                <span className="shrink-0 text-[11.5px] tabular-nums text-fg-muted">
                  #{t.taskNumber ?? t.id}
                </span>
                <StatusBadge status={t.status} size="sm" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/* ---------- 审计日志 ---------- */

function AuditTab() {
  const [action, setAction] = React.useState("");

  const { data: actions = [] } = useQuery<string[]>({
    queryKey: ["audit-actions"],
    queryFn: () => adminApi.auditActions(),
    staleTime: 300_000,
  });

  const {
    data: logs = [],
    isLoading,
    isError,
    refetch,
  } = useQuery<AuditLog[]>({
    queryKey: ["audit-logs", action],
    queryFn: () => adminApi.auditLogs({ action: action || undefined, limit: 200 }),
  });

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-2 border-b border-hairline p-3">
        <div className="w-56">
          <Select value={action} onChange={(e) => setAction(e.target.value)} aria-label="按动作筛选">
            <option value="">全部动作</option>
            {actions.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </Select>
        </div>
        <span className="ml-auto text-[12px] text-fg-muted">{logs.length} 条</span>
      </div>

      {isLoading ? (
        <div className="p-4">
          <SkeletonList rows={6} />
        </div>
      ) : isError ? (
        <ErrorState onRetry={() => void refetch()} />
      ) : logs.length === 0 ? (
        <EmptyState compact title="暂无审计记录" />
      ) : (
        <ul className="divide-y divide-hairline">
          {logs.map((l, i) => (
            <li key={l.id ?? i} className="flex items-center gap-3 px-4 py-2.5">
              <Badge tone="neutral" size="sm" className="shrink-0">
                {l.action ?? "—"}
              </Badge>
              <span className="min-w-0 flex-1 truncate text-[13px] text-fg-secondary">
                {l.detail ?? l.target ?? ""}
              </span>
              <span className="shrink-0 text-[12px] text-fg-muted">
                {l.actorName ?? l.displayName ?? ""}
              </span>
              <span className="shrink-0 text-[11.5px] text-fg-muted">{fromNow(l.createdAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
