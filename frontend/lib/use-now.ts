"use client";

import { useSyncExternalStore } from "react";

/**
 * 获取当前时间戳（每分钟自动刷新一次）
 *
 * 为什么需要它：判断「任务是否逾期」这类逻辑需要读当前时间，但直接在渲染期调用
 * Date.now() 属于**非纯函数调用**，会让组件渲染结果不可预测，并触发
 * react-hooks/purity 报错。React 官方对「外部可变状态」给出的标准解法是
 * useSyncExternalStore —— 把时间当作一个外部数据源来订阅。
 *
 * 用法：
 *   const now = useNow();
 *   const overdue = Date.parse(task.dueDate) < now;
 */

/** 快照：模块级缓存的当前时间戳，由定时器统一推进 */
let currentNow = Date.now();
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | null = null;

const TICK_MS = 60_000;

function subscribe(onChange: () => void) {
  listeners.add(onChange);
  // 首个订阅者启动定时器，避免无谓的空转
  if (timer === null) {
    timer = setInterval(() => {
      currentNow = Date.now();
      listeners.forEach((fn) => fn());
    }, TICK_MS);
  }
  return () => {
    listeners.delete(onChange);
    // 最后一个订阅者卸载后停表
    if (listeners.size === 0 && timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  };
}

function getSnapshot() {
  return currentNow;
}

/** 服务端渲染时的快照：与客户端首次渲染保持一致，避免 hydration 不匹配 */
function getServerSnapshot() {
  return currentNow;
}

export function useNow(): number {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
