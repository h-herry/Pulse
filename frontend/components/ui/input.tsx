"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * 输入框：白面 + 发丝边 + 聚焦时品牌柔光环
 * 苹果风的要点是「安静」——默认几乎无边框强调，聚焦才出现品牌色。
 */
export const Input = React.forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement>
>(({ className, ...props }, ref) => (
  <input
    ref={ref}
    className={cn(
      "w-full h-9 px-3 rounded-[var(--radius-md)]",
      "bg-surface-2 text-fg text-[14px] placeholder:text-fg-muted",
      "border border-transparent",
      "transition-all duration-200 easing-apple",
      "hover:bg-surface-3",
      "focus:bg-surface focus:outline-none focus:border-accent/40 focus:shadow-[var(--shadow-focus)]",
      "disabled:opacity-45 disabled:cursor-not-allowed",
      className
    )}
    {...props}
  />
));
Input.displayName = "Input";

export const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ className, ...props }, ref) => (
  <textarea
    ref={ref}
    className={cn(
      "w-full min-h-[76px] px-3 py-2 rounded-[var(--radius-md)]",
      "bg-surface-2 text-fg text-[14px] placeholder:text-fg-muted leading-relaxed",
      "border border-transparent resize-y",
      "transition-all duration-200 easing-apple",
      "hover:bg-surface-3",
      "focus:bg-surface focus:outline-none focus:border-accent/40 focus:shadow-[var(--shadow-focus)]",
      "disabled:opacity-45",
      className
    )}
    {...props}
  />
));
Textarea.displayName = "Textarea";

export const Label = React.forwardRef<
  HTMLLabelElement,
  React.LabelHTMLAttributes<HTMLLabelElement>
>(({ className, ...props }, ref) => (
  <label
    ref={ref}
    className={cn(
      "block text-[13px] font-medium text-fg-secondary mb-1.5 tracking-[-0.01em]",
      className
    )}
    {...props}
  />
));
Label.displayName = "Label";

/** 表单字段容器：Label + 控件 + 可选说明/错误 */
export function Field({
  label,
  hint,
  error,
  children,
  className,
}: {
  label?: string;
  hint?: string;
  error?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("w-full", className)}>
      {label ? <Label>{label}</Label> : null}
      {children}
      {error ? (
        <p className="mt-1.5 text-[12px] text-danger">{error}</p>
      ) : hint ? (
        <p className="mt-1.5 text-[12px] text-fg-muted">{hint}</p>
      ) : null}
    </div>
  );
}

/** 原生 select 的苹果风包装（带 chevron，跨浏览器稳定） */
export const Select = React.forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, children, ...props }, ref) => (
  <div className="relative w-full">
    <select
      ref={ref}
      className={cn(
        "w-full h-9 pl-3 pr-8 rounded-[var(--radius-md)] appearance-none",
        "bg-surface-2 text-fg text-[14px]",
        "border border-transparent cursor-pointer",
        "transition-all duration-200 easing-apple",
        "hover:bg-surface-3",
        "focus:bg-surface focus:outline-none focus:border-accent/40 focus:shadow-[var(--shadow-focus)]",
        "disabled:opacity-45 disabled:cursor-not-allowed",
        className
      )}
      {...props}
    >
      {children}
    </select>
    <svg
      className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 size-3.5 text-fg-muted"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
  </div>
));
Select.displayName = "Select";
