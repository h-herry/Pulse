"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AuthLink, AuthShell } from "@/components/auth/AuthShell";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/input";
import { useAuthStore } from "@/store/auth";

export default function LoginPage() {
  const router = useRouter();
  const { login, loading } = useAuthStore();
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState("");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!email.trim() || !password) {
      setError("请输入邮箱与密码");
      return;
    }
    try {
      await login(email.trim(), password);
      router.replace("/overview");
    } catch (err) {
      setError((err as Error).message || "登录失败，请重试");
    }
  };

  return (
    <AuthShell
      title="登录 Pulse"
      subtitle="继续管理你的团队任务"
      footer={
        <>
          还没有账户？<AuthLink href="/register">立即注册</AuthLink>
        </>
      }
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="邮箱" error={error}>
          <Input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            autoComplete="email"
            autoFocus
            required
            aria-invalid={!!error}
          />
        </Field>

        <Field label="密码">
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
            required
          />
        </Field>

        <div className="flex justify-end">
          <AuthLink href="/forgot-password">忘记密码？</AuthLink>
        </div>

        <Button
          type="submit"
          variant="primary"
          size="lg"
          className="w-full"
          disabled={loading}
        >
          {loading ? <Loader2 className="size-4 animate-spin" /> : null}
          登录
        </Button>
      </form>
    </AuthShell>
  );
}
