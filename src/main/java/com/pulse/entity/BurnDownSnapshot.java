package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 燃尽图每日快照（对应表 {@code burn_down_snapshots}）。
 *
 * <p>按 {@code snapshotDate} 记录项目（{@code projectId}）在某日的剩余工作量与总量，
 * 用于绘制燃尽图趋势。通过 {@code teamId} 归属团队。
 */
@Data
@TableName("burn_down_snapshots")
public class BurnDownSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 归属项目（外键 projects.id） */
    private Long projectId;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 快照日期（按天） */
    private LocalDate snapshotDate;
    /** 当日剩余工作量计数（计数口径依统计实现，可为任务数或故事点） */
    private Integer remainingCount;
    /** 统计范围内的任务总量 */
    private Integer totalCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
