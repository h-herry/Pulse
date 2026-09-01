import type { ApiResponse } from "./types";

/**
 * Pulse API 客户端
 *
 * 契约（与后端 Spring Boot 严格一致，勿改）：
 * - baseURL: /api/v1（开发态由 next.config rewrites 代理到 :8080）
 * - 鉴权：Authorization: Bearer <token>（无状态，CSRF 已关闭）
 * - 多租户：除 /auth、/teams 外自动注入 X-Team-ID
 * - 响应信封：{ code, message, data }，code===200 成功，code===401 会话失效
 */
const BASE = "/api/v1";

export const TOKEN_KEY = "pulse_token";
export const TEAM_KEY = "pulse_team_id";
export const PROJECT_KEY = "pulse_project_id";

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message);
    this.name = "ApiError";
  }
}

function getToken() {
  if (typeof window === "undefined") return "";
  return window.localStorage.getItem(TOKEN_KEY) ?? "";
}

function getTeamId() {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TEAM_KEY);
}

/** 需要跳过 X-Team-ID 注入的路径（跨租户） */
function isTeamScoped(path: string) {
  return !/^\/(auth|admin)(\/|$)/.test(path) && path !== "/teams";
}

function buildUrl(path: string, params?: Record<string, unknown>) {
  const url = new URL(`${BASE}${path}`, "http://placeholder");
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v === undefined || v === null || v === "") continue;
      url.searchParams.set(k, String(v));
    }
  }
  return `${url.pathname}${url.search}`;
}

/**
 * 会话失效（后端返回 code=401）时的统一处理：清令牌 + 硬跳转登录页。
 *
 * 这里刻意使用 window.location 而非 Next 的路由跳转：本模块是纯 API 层，
 * 拿不到 React 上下文 / router，且会话失效需要一次**整页跳转**来彻底清掉
 * 内存中的查询缓存与 store 状态，客户端软跳转做不到这一点。
 */
function handleUnauthorized() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  const p = window.location.pathname;
  if (!p.startsWith("/login") && !p.startsWith("/register")) {
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination -- 见上方说明：API 层需硬跳转
    window.location.href = "/login";
  }
}

type RequestOptions = {
  params?: Record<string, unknown>;
  body?: unknown;
  /** FormData 直接透传（文件上传） */
  form?: FormData;
  signal?: AbortSignal;
};

async function request<T>(
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {};

  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  if (isTeamScoped(path)) {
    const teamId = getTeamId();
    if (teamId) headers["X-Team-ID"] = teamId;
  }

  let payload: BodyInit | undefined;
  if (options.form) {
    payload = options.form; // 不设 Content-Type，让浏览器补 boundary
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(options.body);
  }

  const res = await fetch(buildUrl(path, options.params), {
    method,
    headers,
    body: payload,
    credentials: "include", // 刷新令牌走 HttpOnly Cookie
    signal: options.signal,
  });

  // 统一解析响应信封：无论 HTTP 状态如何都先尝试从 body 取业务码与消息，
  // 以便「系统异常返回 HTTP 500 + body.code」时仍能拿到原始错误文案，而非泛化提示。
  let envelope: ApiResponse<T> | null = null;
  try {
    envelope = (await res.json()) as ApiResponse<T>;
  } catch {
    envelope = null;
  }

  // 成功：HTTP 2xx 且业务码为 200
  if (res.ok && envelope && envelope.code === 200) {
    return envelope.data;
  }

  // 失败：优先使用 body 中的业务码/消息；网络层无 body 时回退到 HTTP 状态
  const code = envelope && envelope.code ? envelope.code : res.status;
  const message =
    envelope && envelope.message ? envelope.message : `请求失败（HTTP ${res.status}）`;
  if (code === 401 || res.status === 401) handleUnauthorized();
  throw new ApiError(code, message);
}

export const api = {
  get: <T>(path: string, params?: Record<string, unknown>, signal?: AbortSignal) =>
    request<T>("GET", path, { params, signal }),
  post: <T>(path: string, body?: unknown) => request<T>("POST", path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, { body }),
  del: <T>(path: string, body?: unknown) => request<T>("DELETE", path, { body }),
  upload: <T>(path: string, file: File, field = "file") => {
    const form = new FormData();
    form.append(field, file);
    return request<T>("POST", path, { form });
  },
};

/* ============================================================
   按域分组的类型化端点
   ============================================================ */

export const authApi = {
  login: (email: string, password: string) =>
    api.post<import("./types").LoginResponse>("/auth/login", { email, password }),
  register: (email: string, password: string, displayName: string) =>
    api.post<import("./types").LoginResponse>("/auth/register", {
      email,
      password,
      displayName,
    }),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<import("./types").User>("/auth/me"),
  updateMe: (data: { displayName?: string; avatarUrl?: string }) =>
    api.put<import("./types").User>("/auth/me", data),
  changePassword: (oldPassword: string, newPassword: string) =>
    api.post<void>("/auth/change-password", { oldPassword, newPassword }),
  forgotPassword: (email: string) =>
    api.post<void>("/auth/forgot-password", { email }),
  getAiConfig: () => api.get<import("./types").AiConfig>("/auth/ai-config"),
  saveAiConfig: (data: import("./types").AiConfig) =>
    api.post<import("./types").AiConfig>("/auth/ai-config", data),
  deleteAiConfig: () => api.del<void>("/auth/ai-config"),
  testAiConfig: () => api.post<{ ok?: boolean; message?: string }>("/auth/ai-config/test"),
};

export const teamApi = {
  list: () => api.get<import("./types").Team[]>("/teams"),
  get: (id: number) => api.get<import("./types").Team>(`/teams/${id}`),
  create: (data: { name: string; description?: string }) =>
    api.post<import("./types").Team>("/teams", data),
  update: (id: number, data: Partial<import("./types").Team>) =>
    api.patch<import("./types").Team>(`/teams/${id}`, data),
  remove: (id: number) => api.del<void>(`/teams/${id}`),
  members: (id: number) => api.get<import("./types").TeamMember[]>(`/teams/${id}/members`),
  addMember: (id: number, data: { userId: number; role?: string }) =>
    api.post<import("./types").TeamMember>(`/teams/${id}/members`, data),
  updateMember: (id: number, uid: number, data: { role?: string }) =>
    api.patch<import("./types").TeamMember>(`/teams/${id}/members/${uid}`, data),
  removeMember: (id: number, uid: number) =>
    api.del<void>(`/teams/${id}/members/${uid}`),
  projectsProgress: (id: number) =>
    api.get<import("./types").ProjectProgress[]>(`/teams/${id}/projects/progress`),
  tags: (id: number) => api.get<import("./types").Tag[]>(`/teams/${id}/tags`),
  createTag: (id: number, data: { name: string; color: string }) =>
    api.post<import("./types").Tag>(`/teams/${id}/tags`, data),
  updateTag: (id: number, tagId: number, data: { name?: string; color?: string }) =>
    api.put<import("./types").Tag>(`/teams/${id}/tags/${tagId}`, data),
  deleteTag: (id: number, tagId: number) => api.del<void>(`/teams/${id}/tags/${tagId}`),
};

export const projectApi = {
  list: (teamId: number) => api.get<import("./types").Project[]>(`/teams/${teamId}/projects`),
  get: (teamId: number, id: number) =>
    api.get<import("./types").Project>(`/teams/${teamId}/projects/${id}`),
  create: (teamId: number, data: Partial<import("./types").Project>) =>
    api.post<import("./types").Project>(`/teams/${teamId}/projects`, data),
  update: (teamId: number, id: number, data: Partial<import("./types").Project>) =>
    api.patch<import("./types").Project>(`/teams/${teamId}/projects/${id}`, data),
  updateStatus: (teamId: number, id: number, status: string) =>
    api.patch<import("./types").Project>(`/teams/${teamId}/projects/${id}/status`, { status }),
  archive: (teamId: number, id: number) =>
    api.patch<void>(`/teams/${teamId}/projects/${id}/archive`),
  unarchive: (teamId: number, id: number) =>
    api.patch<void>(`/teams/${teamId}/projects/${id}/unarchive`),
  columns: (pid: number) => api.get<import("./types").BoardColumn[]>(`/projects/${pid}/columns`),
  createColumn: (pid: number, data: Partial<import("./types").BoardColumn>) =>
    api.post<import("./types").BoardColumn>(`/projects/${pid}/columns`, data),
  updateColumn: (pid: number, cid: number, data: Partial<import("./types").BoardColumn>) =>
    api.put<import("./types").BoardColumn>(`/projects/${pid}/columns/${cid}`, data),
  reorderColumns: (pid: number, columnIds: number[]) =>
    api.put<void>(`/projects/${pid}/columns/reorder`, { columnIds }),
  deleteColumn: (pid: number, cid: number) => api.del<void>(`/projects/${pid}/columns/${cid}`),
};

export const taskApi = {
  board: (pid: number, iterationId?: number | null) =>
    api.get<import("./types").BoardTask[]>(
      `/projects/${pid}/board`,
      iterationId ? { iterationId } : undefined
    ),
  /** 分页任务列表（IPage<Map>，字段同看板扁平结构，含 dueDate 但不含 startDate） */
  list: (projectId: number, params?: Record<string, unknown>) =>
    api.get<import("./types").PageResult<import("./types").BoardTask>>(
      `/projects/${projectId}/tasks`,
      { page: 1, size: 200, ...(params ?? {}) }
    ),
  /** 「我的任务」与搜索同样返回含 projectKey/assigneeName 的扁平 Map */
  my: () => api.get<import("./types").BoardTask[]>("/tasks/my"),
  search: (q: string, teamId: number, limit = 20) =>
    api.get<import("./types").BoardTask[]>("/tasks/search", { q, teamId, limit }),
  get: (id: number) => api.get<import("./types").Task>(`/tasks/${id}`),
  create: (projectId: number, data: Partial<import("./types").Task>) =>
    api.post<import("./types").Task>(`/projects/${projectId}/tasks`, data),
  update: (id: number, data: Partial<import("./types").Task>) =>
    api.put<import("./types").Task>(`/tasks/${id}`, data),
  patch: (id: number, data: Partial<import("./types").Task>) =>
    api.patch<import("./types").Task>(`/tasks/${id}`, data),
  move: (id: number, data: { boardColumnId?: number; status?: string; sortOrder?: number }) =>
    api.put<import("./types").Task>(`/tasks/${id}/move`, data),
  remove: (id: number) => api.del<void>(`/tasks/${id}`),
  batchUpdate: (taskIds: number[], updates: Record<string, unknown>) =>
    api.patch<void>("/tasks/batch", { taskIds, updates }),
  subtasks: (id: number) => api.get<import("./types").Task[]>(`/tasks/${id}/subtasks`),
  activity: (id: number) => api.get<unknown[]>(`/tasks/${id}/activity`),
  logs: (id: number) => api.get<unknown[]>(`/tasks/${id}/logs`),
  tags: (id: number) => api.get<import("./types").Tag[]>(`/tasks/${id}/tags`),
  addTag: (id: number, tagId: number) => api.post<void>(`/tasks/${id}/tags/${tagId}`),
  removeTag: (id: number, tagId: number) => api.del<void>(`/tasks/${id}/tags/${tagId}`),
};

export const commentApi = {
  list: (taskId: number, page = 1, size = 20) =>
    api.get<import("./types").TaskComment[]>(`/tasks/${taskId}/comments`, { page, size }),
  create: (taskId: number, data: { content: string; mentions?: number[] }) =>
    api.post<import("./types").TaskComment>(`/tasks/${taskId}/comments`, data),
  remove: (taskId: number, commentId: number) =>
    api.del<void>(`/tasks/${taskId}/comments/${commentId}`),
};

export const attachmentApi = {
  list: (taskId: number) => api.get<import("./types").TaskAttachment[]>(`/tasks/${taskId}/attachments`),
  upload: (taskId: number, file: File) =>
    api.upload<import("./types").TaskAttachment>(`/tasks/${taskId}/attachments`, file),
  remove: (taskId: number, id: number) => api.del<void>(`/tasks/${taskId}/attachments/${id}`),
  downloadUrl: (taskId: number, id: number) => `${BASE}/tasks/${taskId}/attachments/${id}/download`,
};

export const iterationApi = {
  list: (pid: number) => api.get<import("./types").Iteration[]>(`/projects/${pid}/iterations`),
  get: (pid: number, id: number) => api.get<import("./types").Iteration>(`/projects/${pid}/iterations/${id}`),
  create: (pid: number, data: Partial<import("./types").Iteration>) =>
    api.post<import("./types").Iteration>(`/projects/${pid}/iterations`, data),
  update: (pid: number, id: number, data: Partial<import("./types").Iteration>) =>
    api.put<import("./types").Iteration>(`/projects/${pid}/iterations/${id}`, data),
  remove: (pid: number, id: number) => api.del<void>(`/projects/${pid}/iterations/${id}`),
  stats: (pid: number, id: number) =>
    api.get<import("./types").IterationStats>(`/projects/${pid}/iterations/${id}/stats`),
  saveRetro: (pid: number, id: number, content: string) =>
    api.put<void>(`/projects/${pid}/iterations/${id}/retro`, { content }),
  retroAiPreview: (pid: number, id: number) =>
    api.post<{ available?: boolean; draft?: string; message?: string }>(
      `/projects/${pid}/iterations/${id}/retro/ai-preview`
    ),
  reportAiPreview: (pid: number, id: number) =>
    api.post<{ available?: boolean; summary?: string; message?: string }>(
      `/projects/${pid}/iterations/${id}/report/ai-preview`
    ),
};

export const reportApi = {
  overview: (teamId: number, projectId?: number) =>
    api.get<import("./types").ReportOverview>(`/teams/${teamId}/reports/overview`, { projectId }),
  trend: (teamId: number, projectId?: number) =>
    api.get<import("./types").TrendResponse>(`/teams/${teamId}/reports/trend`, { projectId }),
  velocity: (teamId: number, projectId?: number) =>
    api.get<import("./types").VelocityPoint[]>(`/teams/${teamId}/reports/velocity`, { projectId }),
  cycleTime: (teamId: number, projectId?: number) =>
    api.get<import("./types").CycleTimeResponse>(`/teams/${teamId}/reports/cycle-time`, { projectId }),
  statusDistribution: (teamId: number, projectId?: number) =>
    api.get<import("./types").DistributionResponse>(`/teams/${teamId}/reports/status-distribution`, { projectId }),
  priorityDistribution: (teamId: number, projectId?: number) =>
    api.get<import("./types").DistributionResponse>(`/teams/${teamId}/reports/priority-distribution`, { projectId }),
  memberEfficiency: (teamId: number, projectId?: number) =>
    api.get<import("./types").MemberEfficiency[]>(`/teams/${teamId}/reports/member-efficiency`, { projectId }),
  iterationReport: (teamId: number, iid: number) =>
    api.get<unknown>(`/teams/${teamId}/reports/iterations/${iid}`),
  exportUrl: (teamId: number, projectId?: number) => {
    const qs = projectId ? `?projectId=${projectId}` : "";
    return `${BASE}/teams/${teamId}/reports/tasks/export${qs}`;
  },
  burndown: (pid: number) => api.get<import("./types").TrendPoint[]>(`/projects/${pid}/reports/burndown`),
  stats: (pid: number) => api.get<import("./types").ReportOverview>(`/projects/${pid}/reports/stats`),
  assignee: (pid: number) => api.get<import("./types").MemberEfficiency[]>(`/projects/${pid}/reports/assignee`),
};

export const notificationApi = {
  list: () => api.get<import("./types").Notification[]>("/notifications"),
  unreadCount: () => api.get<number>("/notifications/unread-count"),
  read: (id: number) => api.post<void>(`/notifications/${id}/read`),
  readAll: () => api.post<void>("/notifications/read-all"),
};

/**
 * 说明：后端已提供「自定义字段 / 保存视图 / 任务依赖 / AI 智能分派与解析」等接口，
 * 但新前端的扁平化任务详情（评论 / 子任务 / 附件三分区）暂未接入这些能力。
 * 按「不留无用代码」的原则，此处不预先保留未使用的客户端封装；
 * 后续需要接入时，按下方 adminApi 的写法补齐即可（接口清单见前端技术文档）。
 */

export const adminApi = {
  users: () => api.get<import("./types").User[]>("/admin/users"),
  teams: () => api.get<import("./types").Team[]>("/admin/teams"),
  projects: () => api.get<import("./types").Project[]>("/admin/projects"),
  archivedProjects: () => api.get<import("./types").Project[]>("/admin/projects/archived"),
  allTasks: (params?: Record<string, unknown>) =>
    api.get<import("./types").Task[]>("/admin/tasks", params),
  members: () => api.get<import("./types").TeamMember[]>("/admin/members"),
  toggleUserEnabled: (id: number, enabled: boolean) =>
    api.put<void>(`/admin/users/${id}/enabled`, { enabled }),
  /** 重置密码（返回临时密码） */
  resetPassword: (id: number) => api.post<void>(`/admin/users/${id}/reset-password`),
  /** 更新用户资料（显示名称、头像） */
  updateUserProfile: (id: number, displayName: string) =>
    api.put<void>(`/admin/users/${id}/profile`, { displayName }),
  /** 更新用户全局角色（user / admin） */
  updateUserRole: (id: number, role: "user" | "admin") =>
    api.put<void>(`/admin/users/${id}/role`, { role }),
  moveMemberTeam: (userId: number, fromTeamId: number, teamId: number) =>
    api.put<void>(`/admin/members/${userId}/team`, { fromTeamId, teamId }),
  auditLogs: (params?: Record<string, unknown>) =>
    api.get<import("./types").AuditLog[]>("/admin/audit-logs", params),
  auditActions: () => api.get<string[]>("/admin/audit-logs/actions"),
};

