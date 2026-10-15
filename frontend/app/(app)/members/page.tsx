"use client";

import * as React from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, Users2 } from "lucide-react";
import { toast } from "sonner";
import { PageShell } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Input, Select } from "@/components/ui/input";
import { UserAvatar } from "@/components/ui/avatar";
import { SkeletonList } from "@/components/ui/skeleton";
import { EmptyState, ErrorState, NoResults } from "@/components/ui/empty-state";
import { adminApi, teamApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useAuthStore } from "@/store/auth";
import type { TeamMember } from "@/lib/types";

/**
 * 成员管理（管理员）
 * 跨团队查看与调配成员，筛选控件平铺在一行。
 */
export default function MembersPage() {
  const queryClient = useQueryClient();
  const { teams } = useContextStore();
  const isAdmin = useAuthStore((s) => !!s.user?.isAdmin);
  const [kw, setKw] = React.useState("");
  const [teamFilter, setTeamFilter] = React.useState("");

  const {
    data: members = [],
    isLoading,
    isError,
    refetch,
  } = useQuery<TeamMember[]>({
    queryKey: ["admin-members"],
    queryFn: () => adminApi.members(),
    enabled: isAdmin,
  });

  const move = useMutation({
    mutationFn: (args: { userId: number; fromTeamId: number; teamId: number }) =>
      adminApi.moveMemberTeam(args.userId, args.fromTeamId, args.teamId),
    onSuccess: () => {
      toast.success("成员已调配");
      void queryClient.invalidateQueries({ queryKey: ["admin-members"] });
      void queryClient.invalidateQueries({ queryKey: ["members"] });
    },
    onError: (e: Error) => toast.error(e.message || "调配失败"),
  });

  const updateRole = useMutation({
    mutationFn: (args: { userId: number; teamId: number; role: string }) =>
      teamApi.updateMember(args.teamId, args.userId, { role: args.role }),
    onSuccess: () => {
      toast.success("团队角色已更新");
      void queryClient.invalidateQueries({ queryKey: ["admin-members"] });
    },
    onError: (e: Error) => toast.error(e.message || "更新失败"),
  });

  if (!isAdmin) {
    return (
      <PageShell title="成员">
        <EmptyState title="无权访问" description="仅系统管理员可查看全部成员" />
      </PageShell>
    );
  }

  const filtered = members.filter((m) => {
    if (teamFilter && String(m.teamId) !== teamFilter) return false;
    const keyword = kw.trim().toLowerCase();
    if (!keyword) return true;
    return (
      (m.displayName ?? "").toLowerCase().includes(keyword) ||
      (m.email ?? "").toLowerCase().includes(keyword)
    );
  });

  return (
    <PageShell title="成员" subtitle={`共 ${members.length} 位成员`}>
      <div className="mb-5 flex flex-wrap items-center gap-2">
        <div className="relative w-52">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-fg-muted" />
          <Input
            value={kw}
            onChange={(e) => setKw(e.target.value)}
            placeholder="搜索姓名或邮箱…"
            className="pl-8"
            aria-label="搜索成员"
          />
        </div>
        <div className="w-44">
          <Select
            value={teamFilter}
            onChange={(e) => setTeamFilter(e.target.value)}
            aria-label="按团队筛选"
          >
            <option value="">全部团队</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </Select>
        </div>
        <span className="ml-auto inline-flex items-center gap-1 text-[12px] text-fg-muted">
          <Users2 className="size-3.5" />
          {filtered.length} 人
        </span>
      </div>

      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonList rows={5} />
          </div>
        ) : isError ? (
          <ErrorState onRetry={() => void refetch()} />
        ) : filtered.length === 0 ? (
          kw.trim() ? (
            <NoResults keyword={kw} />
          ) : (
            <EmptyState compact title="暂无成员" />
          )
        ) : (
          <ul className="divide-y divide-hairline">
            {filtered.map((m) => (
              <li key={m.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                <UserAvatar name={m.displayName ?? m.email} src={m.avatarUrl} size={32} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13.5px] font-medium text-fg">
                    {m.displayName ?? m.email ?? `用户 ${m.userId}`}
                  </p>
                  <p className="truncate text-[12px] text-fg-muted">{m.email}</p>
                </div>

                <div className="w-24 shrink-0">
                  <Select
                    value={m.role}
                    onChange={(e) =>
                      updateRole.mutate({
                        userId: m.userId,
                        teamId: m.teamId,
                        role: e.target.value,
                      })
                    }
                    aria-label="设置团队角色"
                  >
                    <option value="LEAD">组长</option>
                    <option value="USER">成员</option>
                  </Select>
                </div>

                <div className="w-40 shrink-0">
                  <Select
                    value={m.teamId}
                    onChange={(e) =>
                      move.mutate({
                        userId: m.userId,
                        fromTeamId: m.teamId,
                        teamId: Number(e.target.value),
                      })
                    }
                    aria-label="调配到其他团队"
                  >
                    {teams.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </Select>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </PageShell>
  );
}
