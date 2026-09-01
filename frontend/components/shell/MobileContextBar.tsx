"use client";

import { ChevronRight } from "lucide-react";
import { useContextStore } from "@/store/context";
import { TeamSwitcher } from "./TeamSwitcher";
import { ProjectSwitcher } from "./ProjectSwitcher";

/** 移动端上下文行：团队 / 项目 并排显示 */
export function MobileContextBar() {
  const { teamId } = useContextStore();
  if (!teamId) return null;

  return (
    <div className="flex items-center gap-0.5 px-2 pb-2.5">
      <TeamSwitcher />
      <ChevronRight className="size-3.5 shrink-0 text-fg-muted" />
      <ProjectSwitcher />
    </div>
  );
}
