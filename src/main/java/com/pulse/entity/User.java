package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户账户实体（对应表 {@code users}）。
 *
 * <p>除邮箱/密码等身份字段外，承载全局身份状态：
 * <ul>
 *   <li>{@code isSystemAdmin}：系统管理员标记（跨团队全局权限，与团队内角色解耦）；</li>
 *   <li>{@code enabled}：账户启用态，禁用后 {@code JwtAuthFilter} 拒绝其所有请求；</li>
 *   <li>{@code mustChangePassword}：首次登录强制改密标记；</li>
 *   <li>{@code isAdmin}：transient（非表字段），仅用于响应层便捷回显，由 Service 回填。</li>
 * </ul>
 * 团队内角色不在此处，而在 {@code team_members} 关联表中按团队存储。
 */
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    @JsonIgnore
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    @TableField(exist = false)
    private Boolean isAdmin;
    /**
     * R-014: 账户启用状态。true=启用，false=禁用。
     * 默认启用，禁用后 JwtAuthFilter 将拒绝其所有请求。
     */
    @TableField("enabled")
    private Boolean enabled = true;
    /**
     * 系统级管理员标记。与团队内角色(LEAD/MEMBER)完全独立：
     * 系统管理员拥有跨团队全局权限，但不自动成为任何团队的队长。
     */
    @TableField("is_system_admin")
    private Boolean isSystemAdmin = false;
    /**
     * 首次登录改密标记。管理员重置密码 / 新增成员时自动置 true，
     * 用户下次登录后必须改密，避免临时密码明文长期滞留。
     */
    @TableField("must_change_password")
    private Boolean mustChangePassword = false;
    /**
     * 令牌代际号（refresh token 吊销）。改密 / 管理员重置密码 / 启用禁用账户时 +1，
     * 使此前签发的 refresh token 因携带的 {@code tv} 声明与库中值不一致而在下次刷新时被拒。
     * 旧令牌无 tv 声明按 0 处理，与初始值 0 一致，不影响存量登录。
     */
    @TableField("token_version")
    private Integer tokenVersion = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
