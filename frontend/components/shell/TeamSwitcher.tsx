"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Check, ChevronsUpDown, Plus } from "lucide-react";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { useContextStore } from "@/store/context";
import { cn } from "@/lib/utils";

/**
 * 团队切换器
 * 单击展开 → 单击选择，一步切换，不再有「进入团队页再选项目」的中转。
 */
export function TeamSwitcher() {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const { teams, teamId, selectTeam, loadingTeams } = useContextStore();
  const current = teams.find((t) => t.id === teamId);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="max-w-[180px] gap-1.5 px-2"
          aria-label="切换团队"
        >
          <span className="truncate font-medium text-fg">
            {loadingTeams ? "加载中…" : (current?.name ?? "选择团队")}
          </span>
          <ChevronsUpDown className="size-3.5 text-fg-muted" />
        </Button>
      </PopoverTrigger>

      <PopoverContent className="w-60 p-1.5">
        <p className="px-2.5 py-1.5 text-[11px] font-semibold uppercase tracking-wide text-fg-muted">
          团队
        </p>
        <div className="max-h-[280px] overflow-y-auto">
          {teams.map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => {
                void selectTeam(t.id);
                setOpen(false);
              }}
              className={cn(
                "flex w-full items-center justify-between gap-2 rounded-[var(--radius-sm)] px-2.5 py-1.5",
                "text-[13px] transition-colors hover:bg-surface-2",
                t.id === teamId ? "font-medium text-accent" : "text-fg"
              )}
            >
              <span className="truncate">{t.name}</span>
              {t.id === teamId ? <Check className="size-3.5 shrink-0" /> : null}
            </button>
          ))}
          {teams.length === 0 ? (
            <p className="px-2.5 py-3 text-[13px] text-fg-muted">暂无团队</p>
          ) : null}
        </div>

        <div className="mt-1 border-t border-hairline pt-1">
          <button
            type="button"
            onClick={() => {
              setOpen(false);
              router.push("/team");
            }}
            className="flex w-full items-center gap-2 rounded-[var(--radius-sm)] px-2.5 py-1.5 text-[13px] text-fg transition-colors hover:bg-surface-2"
          >
            <Plus className="size-3.5" />
            新建团队
          </button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
