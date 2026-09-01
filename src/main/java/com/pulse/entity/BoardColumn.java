package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 看板列（对应表 {@code board_columns}）。
 *
 * <p>项目看板的列，承载任务（{@code tasks.boardColumnId}）的排布。通过 {@code teamId}/{@code projectId}
 * 双重归属团队与项目。{@code mappedStatus} 将该列与任务状态（status）关联，
 * {@code wipLimit} 为在制品数量上限，{@code sortOrder} 控制显示顺序。
 */
@Data
@TableName("board_columns")
public class BoardColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 归属项目（外键 projects.id） */
    private Long projectId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 列名称 */
    private String name;
    /** 在制品数量上限（Work In Progress Limit），为空/0 表示不限 */
    private Integer wipLimit;
    /** 显示排序（升序） */
    private Integer sortOrder;
    /** 该列映射的任务状态值（与 tasks.status 对应） */
    private String mappedStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
