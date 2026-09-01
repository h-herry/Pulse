package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务依赖关系（R-021）
 *
 * task_id 依赖 depends_on_id（depends_on_id 为前置任务）。
 * 通过后端 DAG 递归校验防止循环依赖。
 */
@Data
@TableName("task_dependencies")
public class TaskDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 被阻塞的任务 */
    private Long taskId;
    /** 阻塞方任务（前置任务） */
    private Long dependsOnId;
    private Long teamId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
