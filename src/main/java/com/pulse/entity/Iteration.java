package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 迭代（对应表 {@code iterations}），如 Sprint。
 *
 * <p>归属于某项目（{@code projectId}）与团队（{@code teamId}），含起止日期与状态。
 * {@code retroContent} 存放迭代回顾（retrospective）内容；任务（{@code tasks.iterationId}）可挂到迭代上。
 */
@Data
@TableName("iterations")
public class Iteration {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 归属项目（外键 projects.id） */
    private Long projectId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 迭代名称 */
    private String name;
    /** 迭代开始日期 */
    private LocalDate startDate;
    /** 迭代结束日期 */
    private LocalDate endDate;
    /** 迭代状态（如 PLANNED / ACTIVE / CLOSED） */
    private String status;
    /** 迭代回顾（retrospective）内容 */
    private String retroContent;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
