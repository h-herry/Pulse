-- Pulse 脉搏 - Database Schema v4
-- All tables use IF NOT EXISTS for idempotent initialization

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500) NULL,
    is_system_admin TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS teams (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS team_members (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    team_id     BIGINT       NOT NULL,
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER',
    joined_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_tm_user_team (user_id, team_id),
    INDEX idx_tm_team (team_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS projects (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    team_id       BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    project_key   VARCHAR(10)  NOT NULL,
    description   TEXT         NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT '项目状态: active=活跃, paused=暂停, deleted=已删除',
    enable_review TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_project_key (team_id, project_key),
    INDEX idx_projects_team (team_id),
    INDEX idx_project_status (status),
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iterations (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    team_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    start_date  DATE         NULL,
    end_date    DATE         NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_iter_project (project_id),
    INDEX idx_iter_team (team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS board_columns (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT      NOT NULL,
    team_id       BIGINT      NOT NULL,
    name          VARCHAR(50) NOT NULL,
    wip_limit     INT         NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    mapped_status VARCHAR(20) NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bc_project (project_id),
    INDEX idx_bc_team (team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tasks (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    team_id         BIGINT       NOT NULL,
    parent_id       BIGINT       NULL,
    assignee_id     BIGINT       NULL,
    reporter_id     BIGINT       NOT NULL,
    iteration_id    BIGINT       NULL,
    board_column_id BIGINT       NULL,
    title           VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'TODO',
    priority        VARCHAR(10)  NOT NULL DEFAULT 'P2',
    story_points    INT          NULL,
    start_date      DATE         NULL,
    due_date        DATE         NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    task_number     INT          NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tasks_project (project_id),
    INDEX idx_tasks_team (team_id),
    INDEX idx_tasks_assignee (assignee_id),
    INDEX idx_tasks_parent (parent_id),
    INDEX idx_tasks_iteration (iteration_id),
    INDEX idx_tasks_column (board_column_id),
    INDEX idx_tasks_due (due_date),
    UNIQUE INDEX idx_tasks_number (project_id, task_number),
    FOREIGN KEY (project_id)      REFERENCES projects(id)      ON DELETE CASCADE,
    FOREIGN KEY (team_id)         REFERENCES teams(id)         ON DELETE CASCADE,
    FOREIGN KEY (parent_id)       REFERENCES tasks(id)         ON DELETE SET NULL,
    FOREIGN KEY (assignee_id)     REFERENCES users(id)         ON DELETE SET NULL,
    FOREIGN KEY (reporter_id)     REFERENCES users(id)         ON DELETE CASCADE,
    FOREIGN KEY (iteration_id)    REFERENCES iterations(id)    ON DELETE SET NULL,
    FOREIGN KEY (board_column_id) REFERENCES board_columns(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_sequences (
    project_id BIGINT NOT NULL PRIMARY KEY,
    next_val    INT    NOT NULL DEFAULT 1,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tags (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    team_id    BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    color      VARCHAR(7)   NOT NULL DEFAULT '#6b7280',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tags_team (team_id),
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_tags (
    id       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id  BIGINT       NOT NULL,
    tag_id   BIGINT       NOT NULL,
    team_id  BIGINT       NOT NULL,
    UNIQUE INDEX idx_tt_unique (task_id, tag_id),
    INDEX idx_tt_team (team_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (tag_id)  REFERENCES tags(id)  ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_activity_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    team_id    BIGINT      NOT NULL,
    action     VARCHAR(50) NOT NULL,
    old_values JSON        NULL,
    new_values JSON        NULL,
    comment    TEXT        NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tal_task_time (task_id, created_at),
    INDEX idx_tal_team (team_id),
    INDEX idx_tal_user (user_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS burn_down_snapshots (
    id              BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT   NOT NULL,
    team_id         BIGINT   NOT NULL,
    snapshot_date   DATE     NOT NULL,
    remaining_count INT      NOT NULL DEFAULT 0,
    total_count     INT      NOT NULL DEFAULT 0,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_burndown_day (project_id, team_id, snapshot_date),
    INDEX idx_bds_project_date (project_id, snapshot_date),
    INDEX idx_bds_team (team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    team_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL DEFAULT 'SYSTEM',
    title      VARCHAR(120) NOT NULL,
    body       VARCHAR(500) NULL,
    link       VARCHAR(300) NULL,
    is_read    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_user_team (user_id, team_id),
    INDEX idx_notif_user_read (user_id, is_read),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 迭代复盘内容 (Retro 自动生成)
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'iterations' AND COLUMN_NAME = 'retro_content');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE iterations ADD COLUMN retro_content TEXT NULL COMMENT ''迭代复盘内容(Markdown)'' AFTER status',
    'SELECT ''iterations.retro_content already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 通知去重键 (智能提醒每日摘要幂等)
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'source_key');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE notifications ADD COLUMN source_key VARCHAR(120) NULL COMMENT ''去重键: 同日同类型提醒只推送一次'' AFTER link',
    'SELECT ''notifications.source_key already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS task_comments (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    team_id    BIGINT       NOT NULL,
    content    TEXT         NOT NULL COMMENT '评论内容（Markdown 格式）',
    mentions   JSON         NULL     COMMENT '@提及的用户ID列表',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tc_task_time (task_id, created_at),
    INDEX idx_tc_team (team_id),
    INDEX idx_tc_user (user_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- R-014: users.enabled 字段（禁用/启用用户）
-- MySQL 8.0+ 条件添加，幂等可重复执行（不依赖 IF NOT EXISTS）
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'enabled');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否启用: 1=启用, 0=禁用'' AFTER avatar_url',
    'SELECT ''users.enabled already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- R-020: 保存的筛选视图
-- ============================================================
CREATE TABLE IF NOT EXISTS saved_views (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    team_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    filters     JSON         NULL     COMMENT '筛选条件 JSON: {assigneeId, tagIds, keyword, ...}',
    sort_config JSON         NULL     COMMENT '排序配置: {field, order}',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sv_project_user (project_id, user_id),
    INDEX idx_sv_team (team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE,
    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- R-021: 任务依赖关系
-- ============================================================
CREATE TABLE IF NOT EXISTS task_dependencies (
    id            BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id       BIGINT   NOT NULL COMMENT '被阻塞的任务',
    depends_on_id BIGINT   NOT NULL COMMENT '阻塞方的任务（前置任务）',
    team_id       BIGINT   NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_td_unique (task_id, depends_on_id),
    INDEX idx_td_team (team_id),
    INDEX idx_td_depends (depends_on_id),
    FOREIGN KEY (task_id)       REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)       REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- R-027: 任务附件
-- ============================================================
CREATE TABLE IF NOT EXISTS task_attachments (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL COMMENT '上传者',
    team_id     BIGINT       NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL COMMENT '存储相对路径（uploads/ 下）',
    file_size   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    file_type   VARCHAR(100) NULL     COMMENT 'MIME 类型',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ta_task (task_id),
    INDEX idx_ta_team (team_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- R-028: 自定义字段定义
-- ============================================================
CREATE TABLE IF NOT EXISTS custom_fields (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    team_id     BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    field_type  VARCHAR(20)  NOT NULL DEFAULT 'text' COMMENT '类型: text/number/date/select/multiselect',
    options     JSON         NULL     COMMENT 'select/multiselect 类型的选项列表',
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cf_project (project_id),
    INDEX idx_cf_team (team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- R-028: 自定义字段值
-- ============================================================
CREATE TABLE IF NOT EXISTS custom_field_values (
    id               BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id          BIGINT  NOT NULL,
    custom_field_id  BIGINT  NOT NULL,
    team_id          BIGINT  NOT NULL,
    value            TEXT    NULL     COMMENT '字段值（select/multiselect 存 JSON 数组）',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_cfv_task_field (task_id, custom_field_id),
    INDEX idx_cfv_team (team_id),
    FOREIGN KEY (task_id)         REFERENCES tasks(id)          ON DELETE CASCADE,
    FOREIGN KEY (custom_field_id) REFERENCES custom_fields(id)  ON DELETE CASCADE,
    FOREIGN KEY (team_id)         REFERENCES teams(id)          ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- 首次登录改密标记（避免临时密码明文长期滞留）
-- 幂等 ALTER：已存在该列则跳过。
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'must_change_password');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''首次登录是否必须改密: 1=必须, 0=否'' AFTER is_system_admin',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 权限模型 v7：新增 users.is_system_admin（系统级管理员，与团队内 LEAD 解耦）
-- 幂等 ALTER：已存在该列则跳过，避免重复启动报错。
-- （CREATE TABLE 已包含该列，此处仅用于存量数据库平滑升级。）
-- ============================================================
SET @db_name = DATABASE();
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = @db_name
      AND table_name   = 'users'
      AND column_name  = 'is_system_admin'
);
SET @alter_sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN is_system_admin TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 权限模型重构：团队内角色 MEMBER 统一重命名为 USER（用户）。
-- 幂等：仅把历史存量 'MEMBER' 记录更新为 'USER'，无 MEMBER 时本语句为空操作。
-- （CREATE TABLE 已使用新默认值 'USER'，此处仅用于存量数据库平滑升级。）
-- ============================================================
UPDATE team_members SET role = 'USER' WHERE role = 'MEMBER';

-- ============================================================
-- 操作审计日志表（v7 增量）：记录敏感操作（成员管理、团队设置、项目增删改、管理员跨团队操作）
-- 操作者姓名在写入时反范式化存储，读取时无需关联用户表。
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT       NULL     COMMENT '操作者用户ID（系统定时任务等无操作者场景为 NULL）',
    actor_name     VARCHAR(100) NULL    COMMENT '操作者显示名（反范式化，便于审计展示）',
    team_id        BIGINT       NULL     COMMENT '操作所属团队（系统管理员跨团队操作时填相关团队）',
    action         VARCHAR(64)  NOT NULL COMMENT '动作类型，如 TEAM_MEMBER_ADD / PROJECT_DELETE',
    target_type    VARCHAR(32)  NULL     COMMENT '目标类型：TEAM / PROJECT / USER / MEMBER',
    target_id      BIGINT       NULL     COMMENT '目标ID',
    detail         VARCHAR(500) NULL     COMMENT '补充信息（如角色变更前后、状态变更）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_action (action),
    INDEX idx_audit_team (team_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- 用户级 LLM 配置表（per-user 自带 Key 模式）
-- api_key_enc 为 AES-256-GCM 密文，绝不存明文；与 users 表分离避免被每请求加载的 User 实体携带密文。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_llm_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    base_url     VARCHAR(512) NULL    COMMENT 'OpenAI 兼容网关地址，缺省用服务端配置',
    model        VARCHAR(128) NULL    COMMENT '模型名，缺省用服务端配置',
    api_key_enc  VARCHAR(1024) NOT NULL COMMENT 'AES-256-GCM 密文（格式 base64(iv):base64(ciphertext)）',
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_ulc_user (user_id),
    INDEX idx_ulc_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- 凭据吊销：users.token_version（令牌代际号）
-- 改密 / 管理员重置密码 / 启用禁用账户时 +1，使此前签发的 refresh token（携带 tv 声明）
-- 在下一次刷新时因失配被拒。默认 0，与旧令牌“无 tv 声明按 0 处理”的兼容规则一致。
-- 幂等 ALTER：已存在该列则跳过（本脚本每次启动都会执行）。
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'token_version');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT ''令牌代际号: 自增后旧 refresh token 失效''',
    'SELECT ''users.token_version already exists''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
