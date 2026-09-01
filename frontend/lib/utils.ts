import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import dayjs from "dayjs";

/** 合并 className（Tailwind 冲突自动去重） */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/** 首字母缩写（头像占位） */
export function initials(name?: string | null) {
  if (!name) return "?";
  const trimmed = name.trim();
  if (!trimmed) return "?";
  // 中文取最后两字，英文取首字母
  if (/[\u4e00-\u9fa5]/.test(trimmed)) {
    return trimmed.slice(-2);
  }
  return trimmed
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

/** 日期格式化（统一 dayjs） */
export function formatDate(
  value?: string | Date | null,
  pattern = "YYYY-MM-DD"
) {
  if (!value) return "—";
  return dayjs(value).format(pattern);
}

/** 相对时间（刚刚 / 3 分钟前 / 2 天前） */
export function fromNow(value?: string | Date | null) {
  if (!value) return "—";
  const target = dayjs(value);
  const diffMin = dayjs().diff(target, "minute");
  if (diffMin < 1) return "刚刚";
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffHour = dayjs().diff(target, "hour");
  if (diffHour < 24) return `${diffHour} 小时前`;
  const diffDay = dayjs().diff(target, "day");
  if (diffDay < 30) return `${diffDay} 天前`;
  return target.format("YYYY-MM-DD");
}

/** 文件大小可读化 */
export function humanSize(bytes?: number | null) {
  if (!bytes && bytes !== 0) return "—";
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let i = 0;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i += 1;
  }
  return `${size.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}
