package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体（v7 增量）。
 * 记录敏感操作（成员管理、团队设置、项目增删改、管理员跨团队操作），
 * 操作者姓名在写入时反范式化存储，便于审计展示时无需关联用户表。
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    /** 动作类型常量（供 Service 调用时引用，避免散落字符串） */
    public static final class Action {
        public static final String TEAM_CREATE = "TEAM_CREATE";
        public static final String TEAM_MEMBER_ADD = "TEAM_MEMBER_ADD";
        public static final String TEAM_MEMBER_REMOVE = "TEAM_MEMBER_REMOVE";
        public static final String TEAM_MEMBER_ROLE_CHANGE = "TEAM_MEMBER_ROLE_CHANGE";
        public static final String TEAM_UPDATE = "TEAM_UPDATE";
        public static final String TEAM_DELETE = "TEAM_DELETE";
        public static final String PROJECT_CREATE = "PROJECT_CREATE";
        public static final String PROJECT_UPDATE = "PROJECT_UPDATE";
        public static final String PROJECT_DELETE = "PROJECT_DELETE";
        public static final String PROJECT_ARCHIVE = "PROJECT_ARCHIVE";
        public static final String PROJECT_UNARCHIVE = "PROJECT_UNARCHIVE";
        public static final String PROJECT_STATUS = "PROJECT_STATUS";
        public static final String ADMIN_USER_ENABLE = "ADMIN_USER_ENABLE";
        public static final String ADMIN_USER_DISABLE = "ADMIN_USER_DISABLE";
        public static final String ADMIN_RESET_PASSWORD = "ADMIN_RESET_PASSWORD";
        public static final String ADMIN_USER_PROFILE = "ADMIN_USER_PROFILE";
        public static final String ADMIN_USER_ROLE = "ADMIN_USER_ROLE";
        public static final String ADMIN_USER_CREATE = "ADMIN_USER_CREATE";
        public static final String ADMIN_MOVE_MEMBER_TEAM = "ADMIN_MOVE_MEMBER_TEAM";

        // —— AI 能力（用户输入 Key，per-user）——
        public static final String AI_KEY_SAVE = "AI_KEY_SAVE";
        public static final String AI_KEY_DELETE = "AI_KEY_DELETE";
        public static final String AI_INVOKE_SUCCESS = "AI_INVOKE_SUCCESS";
        public static final String AI_INVOKE_FAIL = "AI_INVOKE_FAIL";

        private Action() {}
    }

    /** 目标类型 */
    public static final class Target {
        public static final String TEAM = "TEAM";
        public static final String PROJECT = "PROJECT";
        public static final String USER = "USER";
        public static final String MEMBER = "MEMBER";
        public static final String AI = "AI";

        private Target() {}
    }

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private String actorName;
    private Long teamId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private LocalDateTime createdAt;
}
