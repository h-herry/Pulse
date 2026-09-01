package com.pulse.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 团队成员视图对象（返回给前端）。
 *
 * <p>聚合 {@code users} 与 {@code team_members} 信息：{@code userId}/{@code displayName}/{@code email}
 * 来自用户表，{@code role} 为该成员在当前团队内的角色（USER/LEAD），{@code joinedAt} 为加入时间。
 */
public class MemberVO {
    private Long userId;
    private String displayName;
    private String email;
    private String role;
    private LocalDateTime joinedAt;
}
