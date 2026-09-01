"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Check, PartyPopper, Rocket } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/input";
import { PulseLogo } from "@/components/shell/PulseLogo";
import { useContextStore } from "@/store/context";
import { cn } from "@/lib/utils";

const STEPS = [
  { id: 1, title: "创建团队", desc: "团队是协作与数据隔离的基本单位" },
  { id: 2, title: "创建项目", desc: "项目聚合看板、迭代与报表" },
  { id: 3, title: "开始使用", desc: "一切就绪，进入看板开始协作" },
];

/**
 * 新手引导
 *
 * 流程优化：原实现是多步弹窗向导。现在收敛为一页两步表单 + 完成页，
 * 全程不离开当前页面，步骤指示器平铺可见。
 */
export default function OnboardingPage() {
  const router = useRouter();
  const { createTeam, createProject } = useContextStore();

  const [step, setStep] = React.useState(1);
  const [team, setTeam] = React.useState({ name: "", description: "" });
  const [project, setProject] = React.useState({ name: "", description: "" });
  const [busy, setBusy] = React.useState(false);

  const submitTeam = async () => {
    if (!team.name.trim()) return;
    setBusy(true);
    try {
      await createTeam(team.name.trim(), team.description.trim() || undefined);
      setStep(2);
    } catch (e) {
      window.alert((e as Error).message || "创建失败");
    } finally {
      setBusy(false);
    }
  };

  const submitProject = async () => {
    if (!project.name.trim()) return;
    setBusy(true);
    try {
      await createProject({
        name: project.name.trim(),
        description: project.description.trim() || undefined,
      });
      setStep(3);
    } catch (e) {
      window.alert((e as Error).message || "创建失败");
    } finally {
      setBusy(false);
    }
  };

  const skip = () => router.replace("/overview");

  return (
    <div className="grid min-h-screen place-items-center bg-canvas-subtle px-5 py-10">
      <div className="w-full max-w-lg">
        {/* 步骤指示器 */}
        <div className="mb-7 flex items-center justify-center gap-2">
          {STEPS.map((s, i) => {
            const done = step > s.id;
            const active = step === s.id;
            return (
              <React.Fragment key={s.id}>
                <div className="flex flex-col items-center gap-1.5">
                  <span
                    className={cn(
                      "grid size-8 place-items-center rounded-full text-[13px] font-semibold transition-colors",
                      done
                        ? "bg-accent text-white"
                        : active
                          ? "bg-accent-soft text-accent ring-2 ring-accent"
                          : "bg-surface-3 text-fg-muted"
                    )}
                  >
                    {done ? <Check className="size-4" /> : s.id}
                  </span>
                  <span
                    className={cn(
                      "text-[11.5px]",
                      active ? "font-medium text-fg" : "text-fg-muted"
                    )}
                  >
                    {s.title}
                  </span>
                </div>
                {i < STEPS.length - 1 ? (
                  <span
                    className={cn(
                      "mb-5 h-px w-10 transition-colors",
                      step > s.id ? "bg-accent" : "bg-border-strong"
                    )}
                  />
                ) : null}
              </React.Fragment>
            );
          })}
        </div>

        <Card className="p-7">
          {step === 1 ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void submitTeam();
              }}
            >
              <div className="mb-5 flex items-center gap-3">
                <span className="grid size-10 place-items-center rounded-full bg-accent-soft text-accent">
                  <Rocket className="size-5" />
                </span>
                <div>
                  <h1 className="text-[18px] font-semibold tracking-[-0.02em] text-fg">
                    创建你的团队
                  </h1>
                  <p className="text-[13px] text-fg-secondary">{STEPS[0].desc}</p>
                </div>
              </div>

              <div className="space-y-4">
                <Field label="团队名称">
                  <Input
                    value={team.name}
                    onChange={(e) => setTeam((f) => ({ ...f, name: e.target.value }))}
                    placeholder="例如：产品研发组"
                    autoFocus
                  />
                </Field>
                <Field label="简介（可选）">
                  <Textarea
                    value={team.description}
                    onChange={(e) => setTeam((f) => ({ ...f, description: e.target.value }))}
                    placeholder="这个团队负责什么？"
                  />
                </Field>
              </div>

              <div className="mt-6 flex items-center justify-between">
                <Button type="button" variant="ghost" size="sm" onClick={skip}>
                  稍后设置
                </Button>
                <Button type="submit" variant="primary" disabled={!team.name.trim() || busy}>
                  下一步
                </Button>
              </div>
            </form>
          ) : null}

          {step === 2 ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void submitProject();
              }}
            >
              <div className="mb-5 flex items-center gap-3">
                <span className="grid size-10 place-items-center rounded-full bg-accent-soft text-accent">
                  <PulseLogo className="size-6" />
                </span>
                <div>
                  <h1 className="text-[18px] font-semibold tracking-[-0.02em] text-fg">
                    创建第一个项目
                  </h1>
                  <p className="text-[13px] text-fg-secondary">{STEPS[1].desc}</p>
                </div>
              </div>

              <div className="space-y-4">
                <Field label="项目名称">
                  <Input
                    value={project.name}
                    onChange={(e) => setProject((f) => ({ ...f, name: e.target.value }))}
                    placeholder="例如：Pulse 2.0"
                    autoFocus
                  />
                </Field>
                <Field label="描述（可选）" hint="项目标识会自动生成">
                  <Textarea
                    value={project.description}
                    onChange={(e) => setProject((f) => ({ ...f, description: e.target.value }))}
                    placeholder="项目目标与范围"
                  />
                </Field>
              </div>

              <div className="mt-6 flex items-center justify-between">
                <Button type="button" variant="ghost" size="sm" onClick={skip}>
                  跳过
                </Button>
                <Button type="submit" variant="primary" disabled={!project.name.trim() || busy}>
                  完成设置
                </Button>
              </div>
            </form>
          ) : null}

          {step === 3 ? (
            <div className="py-4 text-center">
              <span className="mx-auto mb-4 grid size-14 place-items-center rounded-full bg-success-soft text-success">
                <PartyPopper className="size-7" />
              </span>
              <h1 className="text-[19px] font-semibold tracking-[-0.02em] text-fg">
                一切就绪
              </h1>
              <p className="mx-auto mt-2 max-w-xs text-[13.5px] leading-relaxed text-fg-secondary">
                团队与项目已创建。现在可以邀请成员、创建任务，开始跟踪项目脉搏。
              </p>
              <div className="mt-6 flex justify-center gap-2">
                <Button variant="secondary" onClick={() => router.push("/team")}>
                  邀请成员
                </Button>
                <Button variant="primary" onClick={() => router.push("/board")}>
                  进入看板
                </Button>
              </div>
            </div>
          ) : null}
        </Card>
      </div>
    </div>
  );
}
