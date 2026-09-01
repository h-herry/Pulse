package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务实体（对应表 {@code tasks}）。
 *
 * <p>项目内最小工作单元。可设父任务（{@code parentId}，构成子任务树）、负责人（{@code assigneeId}）、
 * 报告人（{@code reporterId}）、迭代（{@code iterationId}）、看板列（{@code boardColumnId}）、
 * 状态/优先级/故事点/起止与到期日期。通过 {@code teamId}/{@code projectId} 双重归属，
 * 便于团队级隔离与跨项目聚合查询。
 */
@Data
@TableName("tasks")
public class Task {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long teamId;
    private Long parentId;
    private Long assigneeId;
    private Long reporterId;
    private Long iterationId;
    private Long boardColumnId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer sortOrder;
    private Integer taskNumber;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
