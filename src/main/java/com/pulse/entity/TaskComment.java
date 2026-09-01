package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务评论（对应表 {@code task_comments}）。
 *
 * <p>挂靠在某个任务（{@code taskId}）下，由用户（{@code userId}）发表，归属团队（{@code teamId}）。
 * {@code mentions} 记录被 @ 提及的用户 ID（JSON 数组）。{@code displayName}/{@code avatarUrl}
 * 为非持久化字段（{@code @TableField(exist = false)}），由 Service 从用户表联查回填，便于前端直接展示。
 */
@Data
@TableName("task_comments")
public class TaskComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联任务（外键 tasks.id） */
    private Long taskId;
    /** 评论发表人（外键 users.id） */
    private Long userId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 评论正文 */
    private String content;
    /** 被提及（@）的用户 ID 列表（JSON 数组） */
    private String mentions;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String displayName;
    @TableField(exist = false)
    private String avatarUrl;
}
