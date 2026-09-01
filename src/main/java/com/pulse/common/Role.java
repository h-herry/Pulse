package com.pulse.common;

/**
 * 团队内成员角色（按团队维度，与全局系统管理员 is_system_admin 完全解耦）。
 *
 * 三角色模型（重构后统一命名）：
 * - USER（用户）：普通成员，聚焦任务执行，无管理权；
 * - LEAD（组长）：团队内近乎全权（成员/项目/迭代/任务/标签管理），但不跨团队、不管账号；
 * - 系统管理员（管理员）：由 users.is_system_admin 标记，跨团队全局权限，详见 AuthorizationService。
 */
public final class Role {
    /** 用户：团队内普通成员，聚焦任务执行，可查看项目/迭代/成员/标签，不做管理动作。 */
    public static final String USER = "USER";
    /** 组长：团队内近乎全权（成员/项目/迭代/任务/标签管理），但不跨团队、不管账号。 */
    public static final String LEAD = "LEAD";

    private Role() {}
}
