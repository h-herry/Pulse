package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 任务-标签关联（对应表 {@code task_tags}）。
 *
 * <p>任务与标签的多对多关联表。通过 {@code taskId} × {@code tagId} 关联，
 * 并冗余 {@code teamId} 以便团队级隔离与聚合查询。
 */
@Data
@TableName("task_tags")
public class TaskTag {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联任务（外键 tasks.id） */
    private Long taskId;
    /** 关联标签（外键 tags.id） */
    private Long tagId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
}
