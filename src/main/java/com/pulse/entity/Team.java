package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 团队实体（对应表 {@code teams}）。
 *
 * <p>多租户的一级隔离边界：项目、成员、任务、迭代、标签等均以 {@code team_id} 归属某个团队，
 * 绝大多数业务查询以团队为过滤维度。
 */
@Data
@TableName("teams")
public class Team {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
