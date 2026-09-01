"use client";

import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  [
    "inline-flex items-center justify-center gap-1.5 whitespace-nowrap",
    "font-medium tracking-[-0.01em] select-none",
    "transition-all duration-200 easing-apple",
    "focus-visible:outline-none focus-visible:shadow-[var(--shadow-focus)]",
    "disabled:pointer-events-none disabled:opacity-45",
    "[&_svg]:shrink-0",
  ].join(" "),
  {
    variants: {
      variant: {
        /** 主操作：品牌强调色实心 */
        primary:
          "bg-accent text-white shadow-sm hover:bg-accent-hover hover:shadow-md active:bg-accent-active active:scale-[0.98]",
        /** 次级：白面 + 发丝边（苹果最常见的 tertiary 按钮） */
        secondary:
          "bg-surface text-fg border border-hairline shadow-xs hover:bg-surface-2 hover:border-line-strong active:scale-[0.98]",
        /** 文字按钮（导航/取消） */
        ghost:
          "text-fg-secondary hover:bg-surface-2 hover:text-fg active:scale-[0.98]",
        /** 强调文字按钮 */
        accent: "text-accent hover:bg-accent-soft",
        /** 危险操作 */
        danger:
          "bg-danger text-white shadow-sm hover:opacity-90 active:scale-[0.98]",
        /** 危险文字 */
        dangerGhost: "text-danger hover:bg-danger-soft",
      },
      size: {
        sm: "h-7 px-2.5 text-[13px] rounded-[var(--radius-sm)] [&_svg]:size-3.5",
        md: "h-9 px-3.5 text-[14px] rounded-[var(--radius-md)] [&_svg]:size-4",
        lg: "h-11 px-5 text-[15px] rounded-[var(--radius-md)] [&_svg]:size-4",
        icon: "size-9 rounded-[var(--radius-md)] [&_svg]:size-4",
        iconSm: "size-7 rounded-[var(--radius-sm)] [&_svg]:size-3.5",
      },
    },
    defaultVariants: { variant: "secondary", size: "md" },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, type, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        ref={ref}
        type={asChild ? undefined : (type ?? "button")}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { buttonVariants };
