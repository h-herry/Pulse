"use client";

import * as React from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { toast } from "sonner";
import { TOKEN_KEY } from "@/lib/api";
import { useAuthStore } from "@/store/auth";
import { useContextStore } from "@/store/context";

/**
 * 实时协作层（STOMP over SockJS）
 *
 * 后端契约（勿改）：
 * - 端点 /ws（SockJS），CONNECT 头带 Authorization: Bearer <token>
 * - /topic/board.{pid}          → { type: 'moved' | 'updated', taskId, by }
 * - /topic/board.{pid}.presence → 在看板上的在线成员
 * - /topic/board.{pid}.edit     → 谁正在编辑哪个任务
 * - /topic/notifications.{uid}  → 通知推送
 * - 发送：/app/board.{pid}.move | .update | .presence | .edit
 */
export interface BoardEvent {
  type: "moved" | "updated";
  taskId?: number;
  by?: string;
}

let clientRef: Client | null = null;

function token() {
  return typeof window === "undefined" ? "" : window.localStorage.getItem(TOKEN_KEY) ?? "";
}

/** 建立（或复用）STOMP 连接；模块内部使用，全应用共享一条连接 */
function getStompClient(): Client | null {
  if (typeof window === "undefined") return null;

  if (clientRef && clientRef.connected) return clientRef;

  const client = new Client({
    webSocketFactory: () => new SockJS("/ws"),
    connectHeaders: { Authorization: `Bearer ${token()}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: () => {},
  });

  client.activate();
  clientRef = client;
  return client;
}

/** 向某个 STOMP 目的地发送消息（模块内部使用，未连接时静默跳过） */
function publish(destination: string, body: Record<string, unknown>) {
  const client = getStompClient();
  if (!client?.connected) return;
  client.publish({ destination, body: JSON.stringify(body) });
}

/**
 * 订阅看板实时事件
 * 收到 moved / updated 即让相关查询失效，实现多端即时同步。
 * 注意：只失效缓存，不直接改本地状态，避免与乐观更新打架。
 */
export function useBoardRealtime() {
  const queryClient = useQueryClient();
  const { projectId } = useContextStore();
  const user = useAuthStore((s) => s.user);

  React.useEffect(() => {
    if (!projectId || !user) return;

    let cancelled = false;
    const client = getStompClient();
    if (!client) return;

    const subs: { unsubscribe: () => void }[] = [];

    const onConnected = () => {
      if (cancelled) return;

      // 看板变更广播
      subs.push(
        client.subscribe(`/topic/board.${projectId}`, (msg: IMessage) => {
          try {
            const evt = JSON.parse(msg.body) as BoardEvent;
            if (!evt.type) return;
            void queryClient.invalidateQueries({ queryKey: ["board", projectId] });
            void queryClient.invalidateQueries({ queryKey: ["tasks"] });
          } catch {
            /* 忽略无法解析的帧 */
          }
        })
      );

      // 上报在线状态
      publish(`/app/board.${projectId}.presence`, {
        name: user.displayName,
        email: user.email,
      });
    };

    if (client.connected) onConnected();
    else client.onConnect = onConnected;

    return () => {
      cancelled = true;
      subs.forEach((s) => {
        try {
          s.unsubscribe();
        } catch {
          /* 忽略 */
        }
      });
    };
  }, [projectId, user, queryClient]);
}

/** 订阅通知推送 */
export function useNotificationRealtime() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);

  React.useEffect(() => {
    if (!user?.id) return;

    let cancelled = false;
    const client = getStompClient();
    if (!client) return;

    const subs: { unsubscribe: () => void }[] = [];

    const onConnected = () => {
      if (cancelled) return;
      subs.push(
        client.subscribe(`/topic/notifications.${user.id}`, (msg: IMessage) => {
          try {
            const payload = JSON.parse(msg.body) as { title?: string; body?: string };
            void queryClient.invalidateQueries({ queryKey: ["notifications"] });
            if (payload?.title) toast(payload.title, { description: payload.body });
          } catch {
            /* 忽略 */
          }
        })
      );
    };

    if (client.connected) onConnected();
    else client.onConnect = onConnected;

    return () => {
      cancelled = true;
      subs.forEach((s) => {
        try {
          s.unsubscribe();
        } catch {
          /* 忽略 */
        }
      });
    };
  }, [user?.id, queryClient]);
}

/** 通知任务变更给其他端（在乐观更新后调用） */
export function broadcastTaskChange(
  projectId: number,
  type: "move" | "update",
  taskId: number,
  by?: string
) {
  publish(`/app/board.${projectId}.${type}`, { taskId, by: by ?? "" });
}
