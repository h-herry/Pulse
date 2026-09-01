package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务活动日志（对应表 {@code task_activity_log}）。
 *
 * <p>记录任务字段变更流水（每次变更一条）。{@code action} 区分操作类型，
 * {@code oldValues}/{@code newValues} 以 JSON 存储变更前后字段快照，{@code comment} 为可选备注。
 * 通过 {@code taskId}/{@code userId}/{@code teamId} 关联任务、操作人与团队。
 */
@Data
@TableName("task_activity_log")
public class TaskActivityLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联任务（外键 tasks.id） */
    private Long taskId;
    /** 操作人（外键 users.id） */
    private Long userId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 操作类型（如 CREATE / UPDATE / STATUS_CHANGE / COMMENT 等） */
    private String action;
    /** 变更前字段快照（JSON，仅含变更字段） */
    private String oldValues;
    /** 变更后字段快照（JSON，仅含变更字段） */
    private String newValues;
    /** 变更备注（可选） */
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
