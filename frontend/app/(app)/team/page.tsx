"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, Check, Plus, Users2 } from "lucide-react";
import { toast } from "sonner";
import { PageShell, Section } from "@/components/layout/PageShell";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Textarea, Label, Select } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Progress } from "@/components/ui/progress";
import { UserAvatar } from "@/components/ui/avatar";
import { SkeletonList } from "@/components/ui/skeleton";
import { EmptyState, ErrorState } from "@/components/ui/empty-state";
import { projectApi, teamApi } from "@/lib/api";
import { useContextStore } from "@/store/context";
import { useAuthStore } from "@/store/auth";
import { cn } from "@/lib/utils";
import type { Project, TeamMember } from "@/lib/types";

/**
 * 团队与项目
 * 把「团队列表 / 项目列表 / 成员管理」放在同一页的分区里，
 * 替代原先需要在多个层级间跳转才能完成建团队→建项目→加人的流程。
 */
export default function TeamPage() {
  const { teams, teamId, projects, selectTeam, refreshProjects, createTeam, createProject } =
    useContextStore();
  const isAdmin = useAuthStore((s) => !!s.user?.isAdmin);

  const [teamDialog, setTeamDialog] = React.useState(false);
  const [projectDialog, setProjectDialog] = React.useState(false);
  const [teamForm, setTeamForm] = React.useState({ name: "", description: "" });
  const [projectForm, setProjectForm] = React.useState({
    name: "",
    projectKey: "",
    description: "",
  });

  const submitTeam = async () => {
    if (!teamForm.name.trim()) return;
    try {
      await createTeam(teamForm.name.trim(), teamForm.description.trim() || undefined);
      toast.success("团队已创建");
      setTeamDialog(false);
      setTeamForm({ name: "", description: "" });
    } catch (e) {
      toast.error((e as Error).message || "创建失败");
    }
  };

  const submitProject = async () => {
    if (!projectForm.name.trim()) return;
    try {
      await createProject({
        name: projectForm.name.trim(),
        projectKey: projectForm.projectKey.trim() || undefined,
        description: projectForm.description.trim() || undefined,
      });
      toast.success("项目已创建");
      setProjectDialog(false);
      setProjectForm({ name: "", projectKey: "", description: "" });
    } catch (e) {
      toast.error((e as Error).message || "创建失败");
    }
  };

  return (
    <PageShell
      title="团队与项目"
      subtitle="管理协作范围，切换上下文后所有视图自动跟随"
      actions={
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={() => setTeamDialog(true)}>
            <Plus className="size-4" />
            新建团队
          </Button>
          <Button variant="primary" size="sm" onClick={() => setProjectDialog(true)}>
            <Plus className="size-4" />
            新建项目
          </Button>
        </div>
      }
    >
      <div className="grid gap-8 lg:grid-cols-[320px_1fr]">
        {/* 团队列表 */}
        <Section title="团队">
          <Card className="overflow-hidden">
            {teams.length === 0 ? (
              <EmptyState compact title="暂无团队" description="创建团队开始协作" />
            ) : (
              <ul className="divide-y divide-hairline">
                {teams.map((t) => {
                  const active = t.id === teamId;
                  return (
                    <li key={t.id}>
                      <button
                        type="button"
                        onClick={() => void selectTeam(t.id)}
                        className={cn(
                          "flex w-full items-center gap-3 px-4 py-3 text-left transition-colors",
                          active ? "bg-accent-soft" : "hover:bg-surface-2"
                        )}
                        aria-current={active ? "true" : undefined}
                      >
                        <span className="min-w-0 flex-1">
                          <span
                            className={cn(
                              "block truncate text-[13.5px] font-medium",
                              active ? "text-accent" : "text-fg"
                            )}
                          >
                            {t.name}
                          </span>
                          {t.description ? (
                            <span className="block truncate text-[12px] text-fg-muted">
                              {t.description}
                            </span>
                          ) : null}
                        </span>
                        {active ? <Check className="size-4 shrink-0 text-accent" /> : null}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </Card>
        </Section>

        {/* 项目 + 成员 */}
        <div>
          <ProjectsSection projects={projects} onRefresh={refreshProjects} />
          <MembersSection isAdmin={isAdmin} />
        </div>
      </div>

      {/* 新建团队 */}
      <Dialog open={teamDialog} onOpenChange={setTeamDialog}>
        <DialogContent size="sm">
          <DialogHeader>
            <DialogTitle>新建团队</DialogTitle>
            <DialogDescription>团队是协作与数据隔离的基本单位</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <Label htmlFor="team-name">团队名称</Label>
              <Input
                id="team-name"
                value={teamForm.name}
                onChange={(e) => setTeamForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="例如：产品研发组"
                autoFocus
              />
            </div>
            <div>
              <Label htmlFor="team-desc">简介（可选）</Label>
              <Textarea
                id="team-desc"
                value={teamForm.description}
                onChange={(e) => setTeamForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="这个团队负责什么？"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setTeamDialog(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={submitTeam} disabled={!teamForm.name.trim()}>
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 新建项目 */}
      <Dialog open={projectDialog} onOpenChange={setProjectDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建项目</DialogTitle>
            <DialogDescription>项目用于聚合看板、迭代与报表</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <Label htmlFor="pj-name">项目名称</Label>
              <Input
                id="pj-name"
                value={projectForm.name}
                onChange={(e) => setProjectForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="例如：Pulse 2.0"
                autoFocus
              />
            </div>
            <div>
              <Label htmlFor="pj-key">项目标识</Label>
              <Input
                id="pj-key"
                value={projectForm.projectKey}
                onChange={(e) =>
                  setProjectForm((f) => ({ ...f, projectKey: e.target.value.toUpperCase() }))
                }
                placeholder="PULSE（留空自动生成）"
                maxLength={10}
              />
            </div>
            <div>
              <Label htmlFor="pj-desc">描述（可选）</Label>
              <Textarea
                id="pj-desc"
                value={projectForm.description}
                onChange={(e) => setProjectForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="项目目标与范围"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setProjectDialog(false)}>
              取消
            </Button>
            <Button variant="primary" onClick={submitProject} disabled={!projectForm.name.trim()}>
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageShell>
  );
}

/* ---------- 项目 ---------- */

function ProjectsSection({
  projects,
  onRefresh,
}: {
  projects: Project[];
  /** 归档/取消归档后刷新项目列表 */
  onRefresh: () => Promise<void>;
}) {
  const queryClient = useQueryClient();
  const { teamId, projectId, selectProject } = useContextStore();
  const router = useRouter();

  const archive = useMutation({
    mutationFn: (p: Project) =>
      p.status === "ARCHIVED"
        ? projectApi.unarchive(teamId!, p.id)
        : projectApi.archive(teamId!, p.id),
    onSuccess: () => {
      toast.success("已更新项目状态");
      void onRefresh();
      void queryClient.invalidateQueries({ queryKey: ["projects-progress"] });
    },
    onError: (e: Error) => toast.error(e.message || "操作失败"),
  });

  return (
    <Section title="项目" description={`共 ${projects.length} 个项目`}>
      {projects.length === 0 ? (
        <Card>
          <EmptyState
            compact
            title="还没有项目"
            description="创建项目后即可使用看板、迭代与报表"
          />
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {projects.map((p) => {
            const archived = p.status === "ARCHIVED";
            const active = p.id === projectId;
            return (
              <Card
                key={p.id}
                interactive={!archived}
                className={cn("p-4", archived && "opacity-60", active && "border-accent")}
                onClick={() => {
                  if (archived) return;
                  selectProject(p.id);
                  router.push("/board");
                }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate text-[14px] font-medium text-fg">{p.name}</p>
                    <p className="text-[11.5px] uppercase tracking-wide text-fg-muted">
                      {p.projectKey}
                      {archived ? " · 已归档" : ""}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      archive.mutate(p);
                    }}
                    className="grid size-7 shrink-0 place-items-center rounded-[var(--radius-sm)] text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg"
                    aria-label={archived ? "取消归档" : "归档项目"}
                  >
                    <Archive className="size-3.5" />
                  </button>
                </div>

                {p.description ? (
                  <p className="mt-2 line-clamp-2 text-[12.5px] leading-relaxed text-fg-secondary">
                    {p.description}
                  </p>
                ) : null}

                {!archived ? (
                  <div className="mt-3 flex items-center gap-2">
                    <Progress value={p.progress ?? 0} showLabel tone={(p.progress ?? 0) >= 100 ? "done" : "accent"} />
                  </div>
                ) : null}
              </Card>
            );
          })}
        </div>
      )}
    </Section>
  );
}

/* ---------- 成员 ---------- */

function MembersSection({ isAdmin }: { isAdmin: boolean }) {
  const queryClient = useQueryClient();
  const { teamId } = useContextStore();

  const {
    data: members = [],
    isLoading,
    isError,
  } = useQuery<TeamMember[]>({
    queryKey: ["members", teamId],
    queryFn: () => teamApi.members(teamId!),
    enabled: !!teamId,
  });

  const updateRole = useMutation({
    mutationFn: (args: { uid: number; role: string }) =>
      teamApi.updateMember(teamId!, args.uid, { role: args.role }),
    onSuccess: () => {
      toast.success("角色已更新");
      void queryClient.invalidateQueries({ queryKey: ["members", teamId] });
    },
    onError: (e: Error) => toast.error(e.message || "更新失败"),
  });

  return (
    <Section
      title="成员"
      description="团队内的协作成员与角色"
      actions={
        <span className="inline-flex items-center gap-1 text-[12px] text-fg-muted">
          <Users2 className="size-3.5" />
          {members.length} 人
        </span>
      }
    >
      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <SkeletonList rows={3} />
          </div>
        ) : isError ? (
          <ErrorState />
        ) : members.length === 0 ? (
          <EmptyState compact title="暂无成员" />
        ) : (
          <ul className="divide-y divide-hairline">
            {members.map((m) => (
              <li key={m.userId} className="flex items-center gap-3 px-4 py-3">
                <UserAvatar name={m.displayName ?? m.email} src={m.avatarUrl} size={30} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13.5px] font-medium text-fg">
                    {m.displayName ?? m.email ?? `用户 ${m.userId}`}
                  </p>
                  {m.email ? (
                    <p className="truncate text-[12px] text-fg-muted">{m.email}</p>
                  ) : null}
                </div>

                {isAdmin ? (
                  <div className="w-28 shrink-0">
                    <Select
                      value={m.role ?? "USER"}
                      onChange={(e) => updateRole.mutate({ uid: m.userId, role: e.target.value })}
                      aria-label={`修改 ${m.displayName ?? m.userId} 的角色`}
                    >
                      <option value="LEAD">组长</option>
                      <option value="USER">用户</option>
                    </Select>
                  </div>
                ) : (
                  <span className="shrink-0 text-[12px] text-fg-secondary">{m.role}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </Section>
  );
}
