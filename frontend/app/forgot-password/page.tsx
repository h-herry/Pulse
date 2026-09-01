"use client";

import * as React from "react";
import { CheckCircle2, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { AuthLink, AuthShell } from "@/components/auth/AuthShell";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/input";
import { authApi } from "@/lib/api";

export default function ForgotPasswordPage() {
  const [email, setEmail] = React.useState("");
  const [sending, setSending] = React.useState(false);
  const [sent, setSent] = React.useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setSending(true);
    try {
      await authApi.forgotPassword(email.trim());
      setSent(true);
    } catch (err) {
      toast.error((err as Error).message || "发送失败，请稍后重试");
    } finally {
      setSending(false);
    }
  };

  return (
    <AuthShell
      title="重置密码"
      subtitle="输入注册邮箱，我们会发送重置链接"
      footer={
        <>
          想起来了？<AuthLink href="/login">返回登录</AuthLink>
        </>
      }
    >
      {sent ? (
        <div className="py-4 text-center">
          <div className="mx-auto mb-4 grid size-12 place-items-center rounded-full bg-success-soft text-success">
            <CheckCircle2 className="size-6" />
          </div>
          <p className="text-[14px] font-medium text-fg">邮件已发送</p>
          <p className="mt-1.5 text-[13px] leading-relaxed text-fg-secondary">
            请查收 {email} 并按照邮件中的指引重置密码。
          </p>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field label="邮箱">
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoFocus
              required
            />
          </Field>
          <Button type="submit" variant="primary" size="lg" className="w-full" disabled={sending}>
            {sending ? <Loader2 className="size-4 animate-spin" /> : null}
            发送重置链接
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
