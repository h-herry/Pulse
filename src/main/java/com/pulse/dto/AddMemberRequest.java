package com.pulse.dto;

import lombok.Data;

@Data
/**
 * 添加团队成员请求。
 *
 * <p>由团队队长（LEAD）或系统管理员提交：{@code email} 指定被邀请人（已注册则直接加入，
 * 未注册则由系统自动创建账号并下发初始密码），{@code role} 为其在团队内的角色（USER/LEAD），
 * {@code displayName} 为展示名（用于自动建号场景）。
 */
public class AddMemberRequest {
    private String email;
    private String displayName;
    private String role;
}
