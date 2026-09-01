package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目实体（对应表 {@code projects}）。
 *
 * <p>归属某团队（{@code teamId}），是任务、看板列、迭代、标签等资源的容器。
 * {@code status} 取值含 {@code active}/{@code archived}/{@code deleted}（软删除，查询需排除 deleted）。
 */
@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String name;
    private String projectKey;
    private String description;
    private String status = "active";
    private Integer enableReview;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
