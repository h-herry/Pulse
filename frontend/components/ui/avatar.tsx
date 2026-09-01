"use client";

import * as React from "react";
import * as AvatarPrimitive from "@radix-ui/react-avatar";
import { cn, initials } from "@/lib/utils";

export const Avatar = React.forwardRef<
  React.ComponentRef<typeof AvatarPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Root>
>(({ className, ...props }, ref) => (
  <AvatarPrimitive.Root
    ref={ref}
    className={cn(
      "relative inline-flex shrink-0 select-none overflow-hidden rounded-full align-middle",
      "bg-accent-soft ring-1 ring-hairline",
      className
    )}
    {...props}
  />
));
Avatar.displayName = "Avatar";

export const AvatarImage = AvatarPrimitive.Image;

export const AvatarFallback = React.forwardRef<
  React.ComponentRef<typeof AvatarPrimitive.Fallback>,
  React.ComponentPropsWithoutRef<typeof AvatarPrimitive.Fallback>
>(({ className, ...props }, ref) => (
  <AvatarPrimitive.Fallback
    ref={ref}
    className={cn(
      "flex h-full w-full items-center justify-center",
      "bg-accent-soft text-accent text-[11px] font-semibold leading-none",
      className
    )}
    {...props}
  />
));
AvatarFallback.displayName = "AvatarFallback";

/** 便捷用户头像：自动取首字母/末两字 */
export function UserAvatar({
  name,
  src,
  size = 24,
  className,
}: {
  name?: string | null;
  src?: string | null;
  size?: number;
  className?: string;
}) {
  return (
    <Avatar style={{ width: size, height: size }} className={className}>
      {src ? <AvatarImage src={src} alt={name ?? ""} /> : null}
      <AvatarFallback>{initials(name)}</AvatarFallback>
    </Avatar>
  );
}
