package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 团队-成员关联实体（对应表 {@code team_members}）。
 *
 * <p>表达「某用户在某团队内的从属关系」与「本团队角色」{@code role}（{@code USER}/{@code LEAD}）。
 * 一个用户可属于多个团队，且角色按团队独立——这是多租户隔离的基础关系表，
 * 授权判定（是否队长/成员）均依赖此表。
 */
@Data
@TableName("team_members")
public class TeamMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long teamId;
    private String role;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
