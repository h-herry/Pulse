"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AuthLink, AuthShell } from "@/components/auth/AuthShell";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/input";
import { useAuthStore } from "@/store/auth";

export default function RegisterPage() {
  const router = useRouter();
  const { register, loading } = useAuthStore();
  const [form, setForm] = React.useState({ email: "", password: "", confirm: "", displayName: "" });
  const [error, setError] = React.useState("");

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!form.displayName.trim()) return setError("请填写姓名");
    if (!form.email.trim()) return setError("请填写邮箱");
    if (form.password.length < 6) return setError("密码至少 6 位");
    if (form.password !== form.confirm) return setError("两次输入的密码不一致");

    try {
      await register(form.email.trim(), form.password, form.displayName.trim());
      router.replace("/onboarding");
    } catch (err) {
      setError((err as Error).message || "注册失败，请重试");
    }
  };

  return (
    <AuthShell
      title="创建账户"
      subtitle="一分钟开始团队协作"
      footer={
        <>
          已有账户？<AuthLink href="/login">去登录</AuthLink>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="姓名">
          <Input
            value={form.displayName}
            onChange={set("displayName")}
            placeholder="你的名字"
            autoFocus
            required
          />
        </Field>

        <Field label="邮箱">
          <Input
            type="email"
            value={form.email}
            onChange={set("email")}
            placeholder="you@example.com"
            autoComplete="email"
            required
          />
        </Field>

        <Field label="密码" hint="至少 6 位">
          <Input
            type="password"
            value={form.password}
            onChange={set("password")}
            placeholder="••••••••"
            autoComplete="new-password"
            required
          />
        </Field>

        <Field label="确认密码" error={error}>
          <Input
            type="password"
            value={form.confirm}
            onChange={set("confirm")}
            placeholder="••••••••"
            autoComplete="new-password"
            required
            aria-invalid={!!error}
          />
        </Field>

        <Button type="submit" variant="primary" size="lg" className="w-full" disabled={loading}>
          {loading ? <Loader2 className="size-4 animate-spin" /> : null}
          创建账户
        </Button>
      </form>
    </AuthShell>
  );
}
