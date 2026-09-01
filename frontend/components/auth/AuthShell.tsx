"use client";

import * as React from "react";
import Link from "next/link";
import { PulseLogo } from "@/components/shell/PulseLogo";

/**
 * 认证页外壳
 * 苹果官网式的登录观感：大面积留白、居中卡片、克制的品牌标识。
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="grid min-h-screen place-items-center bg-canvas-subtle px-5 py-10">
      <div className="w-full max-w-[380px]">
        <div className="mb-7 flex flex-col items-center text-center">
          <PulseLogo className="mb-4 size-12" />
          <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-fg">{title}</h1>
          {subtitle ? (
            <p className="mt-1.5 text-[13.5px] leading-relaxed text-fg-secondary">{subtitle}</p>
          ) : null}
        </div>

        <div className="rounded-[var(--radius-lg)] border border-hairline bg-surface p-6 shadow-md">
          {children}
        </div>

        {footer ? (
          <p className="mt-5 text-center text-[13px] text-fg-secondary">{footer}</p>
        ) : null}

        <p className="mt-8 text-center text-[12px] text-fg-muted">
          Pulse 脉搏 · 让每个项目都有自己的脉搏
        </p>
      </div>
    </div>
  );
}

export function AuthLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="font-medium text-accent hover:underline">
      {children}
    </Link>
  );
}
