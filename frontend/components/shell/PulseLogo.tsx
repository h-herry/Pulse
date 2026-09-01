import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * 品牌标识 —— Pulse（脉搏）
 *
 * 构成意象：圆底为品牌主色，中间折线是「心电图/脉搏」，峰值处的金色圆点代表
 * 关键时刻的聚焦，右下角三条横线 + 对勾点明「任务管理」属性。
 *
 * 配色（2026-08-30 随品牌色调切换同步更新）：
 * - 圆底：品牌蓝 #0071E3（与 --accent 浅色值一致）
 * - 脉搏线 / 任务线：纯白，靠 opacity 分层
 * - 峰值圆点与对勾：品牌金 #F9D342，保留「关键聚焦」的品牌记忆点
 *
 * 所有配色与 globals.css 的 --accent 保持同一品牌色，改动 --accent 时请一并核对此处。
 */
export function PulseLogo({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 512 512"
      className={cn("shrink-0", className)}
      role="img"
      aria-label="Pulse 脉搏"
    >
      {/* 圆底：品牌蓝 */}
      <rect width="512" height="512" rx="96" ry="96" fill="#0071E3" />

      {/* 脉搏线：居中折线，峰值偏下形成视觉重心 */}
      <polyline
        points="56,280 140,280 190,140 256,360 320,180 370,300 420,280 456,280"
        fill="none"
        stroke="#FFFFFF"
        strokeWidth="28"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* 峰值高亮点：品牌金，象征关键时刻的聚焦与爆发 */}
      <circle cx="256" cy="360" r="18" fill="#F9D342" />

      {/* 脉搏线上的任务节点，透明度区分主次 */}
      <circle cx="140" cy="280" r="15" fill="#FFFFFF" opacity="0.5" />
      <circle cx="320" cy="180" r="18" fill="#FFFFFF" />
      <circle cx="420" cy="280" r="15" fill="#FFFFFF" opacity="0.6" />

      {/* 右下角任务清单三条线：缩短内收，不拉偏整体重心 */}
      <line x1="400" y1="430" x2="460" y2="430" stroke="#FFFFFF" strokeWidth="10" strokeLinecap="round" opacity="0.6" />
      <line x1="400" y1="455" x2="445" y2="455" stroke="#FFFFFF" strokeWidth="10" strokeLinecap="round" opacity="0.4" />
      <line x1="400" y1="480" x2="430" y2="480" stroke="#FFFFFF" strokeWidth="10" strokeLinecap="round" opacity="0.25" />

      {/* 对勾：紧贴清单上方，维持重心 */}
      <polyline
        points="435,445 450,462 475,432"
        fill="none"
        stroke="#F9D342"
        strokeWidth="12"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.85"
      />
    </svg>
  );
}
