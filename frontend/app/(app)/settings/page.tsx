"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, Monitor, Moon, Sun } from "lucide-react";
import { toast } from "sonner";
import { PageShell } from "@/components/layout/PageShell";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Field, Input, Select } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { authApi } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { useThemeStore, type ThemeMode } from "@/store/theme";
import { cn } from "@/lib/utils";
import type { AiConfig, User } from "@/lib/types";

/** 设置卡片统一骨架（表单数据未就绪时使用） */
function SettingsSkeleton() {
  return (
    <Card className="max-w-xl">
      <CardContent className="space-y-4 pt-5">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </CardContent>
    </Card>
  );
}

/**
 * 设置
 *
 * 流程优化关键点：原先「个人资料 / AI 配置 / 修改密码」分散在头像菜单的
 * 三个独立 Modal 中（二级弹窗）。现在合并为一个页面的四个标签页，
 * 一次进入、平级切换。
 */
export default function SettingsPage() {
  // user 为异步恢复（Zustand persist 注水），未就绪时不挂载表单，
  // 从而让表单能用「初始值 + key」一次性初始化，省掉 useEffect 回填。
  const user = useAuthStore((s) => s.user);

  return (
    <PageShell title="设置" subtitle="管理账户、安全与外观偏好">
      <Tabs defaultValue="profile">
        <TabsList>
          <TabsTrigger value="profile">个人资料</TabsTrigger>
          <TabsTrigger value="security">安全</TabsTrigger>
          <TabsTrigger value="ai">AI 配置</TabsTrigger>
          <TabsTrigger value="appearance">外观</TabsTrigger>
        </TabsList>

        <TabsContent value="profile">
          {user ? (
            <ProfileForm key={user.id} user={user} />
          ) : (
            <SettingsSkeleton />
          )}
        </TabsContent>
        <TabsContent value="security">
          <PasswordForm />
        </TabsContent>
        <TabsContent value="ai">
          <AiConfigSection />
        </TabsContent>
        <TabsContent value="appearance">
          <AppearanceForm />
        </TabsContent>
      </Tabs>
    </PageShell>
  );
}

/* ---------- 个人资料 ---------- */

function ProfileForm({ user }: { user: User }) {
  const queryClient = useQueryClient();
  const setUser = useAuthStore((s) => s.setUser);

  /*
   * 编辑态初值直接取自 user。父层用 key={user.id} 挂载本组件，
   * 切换账户时整体重挂载即完成重置，无需 useEffect 同步。
   */
  const [displayName, setDisplayName] = React.useState(user.displayName ?? "");
  const [avatarUrl, setAvatarUrl] = React.useState(user.avatarUrl ?? "");

  const save = useMutation({
    mutationFn: () => authApi.updateMe({ displayName, avatarUrl }),
    onSuccess: (updated) => {
      setUser(updated);
      void queryClient.invalidateQueries({ queryKey: ["members"] });
      toast.success("资料已更新");
    },
    onError: (e: Error) => toast.error(e.message || "保存失败"),
  });

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>个人资料</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <Field label="邮箱" hint="邮箱作为登录账号，暂不支持修改">
          <Input value={user.email} disabled readOnly />
        </Field>
        <Field label="显示名称">
          <Input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="你的名字"
          />
        </Field>
        <Field label="头像地址" hint="留空则使用姓名首字母生成的头像">
          <Input
            value={avatarUrl}
            onChange={(e) => setAvatarUrl(e.target.value)}
            placeholder="https://…"
          />
        </Field>
        <div className="flex justify-end">
          <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            保存
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

/* ---------- 安全 ---------- */

function PasswordForm() {
  const [form, setForm] = React.useState({ old: "", next: "", confirm: "" });
  const [error, setError] = React.useState("");

  const change = useMutation({
    mutationFn: () => authApi.changePassword(form.old, form.next),
    onSuccess: () => {
      toast.success("密码已修改");
      setForm({ old: "", next: "", confirm: "" });
    },
    onError: (e: Error) => setError(e.message || "修改失败"),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!form.old || !form.next) return setError("请填写完整");
    if (form.next.length < 6) return setError("新密码至少 6 位");
    if (form.next !== form.confirm) return setError("两次输入的新密码不一致");
    change.mutate();
  };

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>修改密码</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field label="当前密码">
            <Input
              type="password"
              value={form.old}
              onChange={(e) => setForm((f) => ({ ...f, old: e.target.value }))}
              autoComplete="current-password"
            />
          </Field>
          <Field label="新密码" hint="至少 6 位">
            <Input
              type="password"
              value={form.next}
              onChange={(e) => setForm((f) => ({ ...f, next: e.target.value }))}
              autoComplete="new-password"
            />
          </Field>
          <Field label="确认新密码" error={error}>
            <Input
              type="password"
              value={form.confirm}
              onChange={(e) => setForm((f) => ({ ...f, confirm: e.target.value }))}
              autoComplete="new-password"
              aria-invalid={!!error}
            />
          </Field>
          <div className="flex justify-end">
            <Button type="submit" variant="primary" disabled={change.isPending}>
              {change.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              更新密码
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}

/* ---------- AI 配置 ---------- */

/** 配置加载壳：加载中显示骨架，加载完再挂载表单（保证表单能用初值一次性初始化） */
function AiConfigSection() {
  const { data: config, isLoading } = useQuery<AiConfig>({
    queryKey: ["ai-config"],
    queryFn: () => authApi.getAiConfig(),
  });

  if (isLoading) return <SettingsSkeleton />;
  // key 固定：保存后配置刷新不会把用户正在编辑的表单重置掉
  return <AiConfigForm key="ai-config" initial={config ?? {}} />;
}

function AiConfigForm({ initial }: { initial: AiConfig }) {
  /*
   * 初值来自首次加载到的配置。父层保证「加载完成后才挂载」，因此这里可以
   * 直接用 useState 初始化，不需要 useEffect 回填；key 固定不变，
   * 保存后重新拉取配置也不会把用户正在编辑的内容冲掉。
   */
  const [form, setForm] = React.useState<AiConfig>({
    baseUrl: initial.baseUrl ?? "",
    apiKey: initial.apiKey ?? "",
    model: initial.model ?? "",
    enabled: initial.enabled ?? false,
  });

  const save = useMutation({
    mutationFn: () => authApi.saveAiConfig(form),
    onSuccess: () => toast.success("AI 配置已保存"),
    onError: (e: Error) => toast.error(e.message || "保存失败"),
  });

  const test = useMutation({
    mutationFn: () => authApi.testAiConfig(),
    onSuccess: (r) => toast.success(r?.message ?? "连接成功"),
    onError: (e: Error) => toast.error(e.message || "连接失败"),
  });

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>AI 配置</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="rounded-[var(--radius-md)] bg-info-soft px-3 py-2.5 text-[12.5px] leading-relaxed text-info">
          密钥仅保存在你自己的服务端账户中，用于生成迭代复盘草稿与智能分派建议。
        </p>

        <Field label="接口地址">
          <Input
            value={form.baseUrl ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, baseUrl: e.target.value }))}
            placeholder="https://api.openai.com/v1"
          />
        </Field>
        <Field label="API Key" hint="留空表示不修改已保存的密钥">
          <Input
            type="password"
            value={form.apiKey ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, apiKey: e.target.value }))}
            placeholder="sk-…"
            autoComplete="off"
          />
        </Field>
        <Field label="模型">
          <Input
            value={form.model ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, model: e.target.value }))}
            placeholder="gpt-4o-mini"
          />
        </Field>
        <Field label="启用状态">
          <Select
            value={form.enabled ? "1" : "0"}
            onChange={(e) => setForm((f) => ({ ...f, enabled: e.target.value === "1" }))}
          >
            <option value="1">启用</option>
            <option value="0">停用</option>
          </Select>
        </Field>

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => test.mutate()} disabled={test.isPending}>
            {test.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            测试连接
          </Button>
          <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
            保存
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

/* ---------- 外观 ---------- */

function AppearanceForm() {
  const { mode, setMode } = useThemeStore();

  const options: { value: ThemeMode; label: string; icon: React.ReactNode; desc: string }[] = [
    {
      value: "light",
      label: "浅色",
      icon: <Sun className="size-4" />,
      desc: "苹果纯白画布，适合日间使用",
    },
    {
      value: "dark",
      label: "深色",
      icon: <Moon className="size-4" />,
      desc: "锌灰表面，降低夜间视觉疲劳",
    },
    {
      value: "system",
      label: "跟随系统",
      icon: <Monitor className="size-4" />,
      desc: "根据设备设置自动切换",
    },
  ];

  return (
    <Card className="max-w-xl">
      <CardHeader>
        <CardTitle>外观</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid gap-2 sm:grid-cols-3">
          {options.map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => setMode(o.value)}
              className={cn(
                "flex flex-col items-start gap-2 rounded-[var(--radius-md)] border p-3.5 text-left",
                "transition-all duration-200 easing-apple",
                mode === o.value
                  ? "border-accent bg-accent-soft shadow-sm"
                  : "border-hairline hover:border-line-strong hover:bg-surface-2"
              )}
              aria-pressed={mode === o.value}
            >
              <span
                className={cn(
                  "grid size-8 place-items-center rounded-full",
                  mode === o.value ? "bg-accent text-white" : "bg-surface-3 text-fg-secondary"
                )}
              >
                {o.icon}
              </span>
              <span className="text-[13.5px] font-medium text-fg">{o.label}</span>
              <span className="text-[11.5px] leading-relaxed text-fg-muted">{o.desc}</span>
            </button>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
