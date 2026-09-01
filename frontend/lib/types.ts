/**
 * Pulse 脉搏 · 领域类型
 * 字段严格对齐后端 Java 实体（src/main/java/com/pulse/entity）。
 */

/** 后端统一响应信封 */
export interface ApiResponse<T = unknown> {
  code: number;
  message?: string;
  data: T;
}

export type TaskStatus =
  | "TODO"
  | "IN_PROGRESS"
  | "REVIEW"
  | "DONE";

export type Priority = "P0" | "P1" | "P2" | "P3" | "P4" | "P5";

export interface User {
  id: number;
  email: string;
  displayName: string;
  avatarUrl?: string | null;
  /** 系统角色：user / admin（全局管理员，由后端 is_system_admin 驱动） */
  role?: "user" | "admin";
  /** 兼容旧字段（后端 isSystem_admin） */
  isAdmin: boolean;
  /** 账户是否启用（false = 已冻结） */
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  displayName: string;
  email: string;
}

export interface Team {
  id: number;
  name: string;
  description?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface TeamMember {
  id: number;
  userId: number;
  teamId: number;
  role: string;
  joinedAt?: string;
  /** 成员列表接口会联表补充用户信息 */
  displayName?: string | null;
  email?: string | null;
  avatarUrl?: string | null;
}

export interface Project {
  id: number;
  teamId: number;
  name: string;
  projectKey: string;
  description?: string | null;
  enableReview?: number;
  status?: string | null;
  createdAt?: string;
  updatedAt?: string;
  /** 列表接口补充字段 */
  progress?: number;
  taskCount?: number;
  doneCount?: number;
  memberCount?: number;
}

/**
 * 团队项目进度（GET /teams/{id}/projects/progress）
 * 后端返回 List<Map>，字段为 projectId / projectName（非 Project 的 id / name）。
 */
export interface ProjectProgress {
  projectId: number;
  projectName: string;
  projectKey: string;
  taskTotal: number;
  taskDone: number;
  progress: number;
}

export interface BoardColumn {
  id: number;
  projectId: number;
  teamId: number;
  name: string;
  wipLimit?: number | null;
  sortOrder: number;
  mappedStatus: string;
}

export interface Tag {
  id: number;
  teamId: number;
  name: string;
  color: string;
}

export interface TaskTag {
  id: number;
  name: string;
  color: string;
}

export interface Task {
  id: number;
  projectId: number;
  teamId: number;
  parentId?: number | null;
  assigneeId?: number | null;
  reporterId?: number | null;
  iterationId?: number | null;
  boardColumnId?: number | null;
  title: string;
  description?: string | null;
  status: TaskStatus | string;
  priority: Priority | string;
  storyPoints?: number | null;
  startDate?: string | null;
  dueDate?: string | null;
  sortOrder?: number;
  taskNumber?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 看板任务（GET /projects/{pid}/board）
 * 后端返回扁平 Map 列表，前端按 boardColumnId 分组。
 */
export interface BoardTask {
  id: number;
  title: string;
  status: string;
  priority: string;
  assigneeId?: number | null;
  boardColumnId?: number | null;
  iterationId?: number | null;
  storyPoints?: number | null;
  dueDate?: string | null;
  taskNumber?: number | null;
  sortOrder?: number | null;
  description?: string | null;
  projectKey?: string | null;
  assigneeName?: string | null;
  tags?: TaskTag[];
}

export interface Iteration {
  id: number;
  projectId: number;
  teamId: number;
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  status?: string | null;
  retroContent?: string | null;
  createdAt?: string;
  updatedAt?: string;
  /** 列表接口补充 */
  taskCount?: number;
  doneCount?: number;
  completedPoints?: number;
  totalPoints?: number;
}

export interface IterationStats {
  total?: number;
  done?: number;
  totalPoints?: number;
  completedPoints?: number;
  completionRate?: number;
}

export interface TaskComment {
  id: number;
  taskId: number;
  userId: number;
  teamId: number;
  content: string;
  mentions?: string | null;
  createdAt?: string;
  updatedAt?: string;
  displayName?: string | null;
  avatarUrl?: string | null;
}

export interface TaskAttachment {
  id: number;
  taskId: number;
  userId: number;
  teamId: number;
  fileName: string;
  filePath: string;
  fileSize?: number | null;
  fileType?: string | null;
  createdAt?: string;
}

export interface Notification {
  id: number;
  userId: number;
  teamId: number;
  type: string;
  title: string;
  body?: string | null;
  link?: string | null;
  sourceKey?: string | null;
  isRead?: number;
  createdAt?: string;
}

/**
 * 说明：后端还存在「自定义字段 / 保存视图 / 任务依赖」等实体，
 * 因前端暂未接入对应能力（见 lib/api.ts 同名说明），这里不保留未使用的类型定义。
 */

/**
 * 报表：概览统计（GET /teams/{teamId}/reports/overview）
 * 后端返回 Map：total / done / overdue / progress
 */
export interface ReportOverview {
  total?: number;
  done?: number;
  overdue?: number;
  /** 完成率（整数百分比） */
  progress?: number;
}

/** 报表：燃尽趋势（GET /teams/{tid}/reports/trend 返回 { projectId, data: [...] }） */
export interface TrendResponse {
  projectId?: number;
  data?: TrendPoint[];
}

/** 字段按后端可能命名做兼容（date/snapshotDate、remaining/remainingPoints） */
export interface TrendPoint {
  date?: string;
  snapshotDate?: string;
  remaining?: number;
  remainingPoints?: number;
  ideal?: number;
  idealPoints?: number;
}

/** 报表：速率（[{ name, completedStoryPoints }]） */
export interface VelocityPoint {
  name?: string;
  completedStoryPoints?: number;
}

/** 报表：分布（{ items: [{ status|priority, count }] }） */
export interface DistributionResponse {
  items?: DistributionItem[];
}

export interface DistributionItem {
  status?: string;
  priority?: string;
  count?: number;
}

/** 报表：周期时间（{ buckets: [{ range, count }] }） */
export interface CycleTimeResponse {
  buckets?: { range?: string; count?: number }[];
}

export interface MemberEfficiency {
  userId?: number;
  displayName?: string;
  assigned?: number;
  completed?: number;
  avgCycleTime?: number;
}

/** 操作审计日志 */
export interface AuditLog {
  id?: number;
  action?: string;
  actorName?: string;
  displayName?: string;
  detail?: string;
  target?: string;
  teamId?: number;
  createdAt?: string;
}

/** MyBatis-Plus 分页结果（IPage 序列化） */
export interface PageResult<T> {
  records?: T[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
}

export interface AiConfig {
  baseUrl?: string;
  apiKey?: string;
  model?: string;
  enabled?: boolean;
}
