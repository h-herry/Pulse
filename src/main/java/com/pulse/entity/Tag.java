package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 标签实体（对应表 {@code tags}）。
 *
 * <p>团队级（{@code teamId}）可复用标签，用于给任务打标（{@code task_tags} 关联）。
 * {@code color} 为展示用颜色（如十六进制色值）。
 */
@Data
@TableName("tags")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 归属团队（外键 teams.id，多租户隔离边界） */
    private Long teamId;
    /** 标签名称 */
    private String name;
    /** 展示颜色（如 #RRGGBB） */
    private String color;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
