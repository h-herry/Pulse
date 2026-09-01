package com.pulse.common;

public class TenantContext {
    private static final ThreadLocal<Long> tenantHolder = new ThreadLocal<>();
    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> systemAdminHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> globalViewHolder = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { tenantHolder.set(tenantId); }
    public static Long getTenantId() { return tenantHolder.get(); }
    public static void setUserId(Long userId) { userIdHolder.set(userId); }
    public static Long getUserId() { return userIdHolder.get(); }

    /**
     * 系统级管理员标记（与团队内角色 LEAD 完全分离）。
     * 仅由 JwtAuthFilter 依据 users.is_system_admin 设置。
     */
    public static void setSystemAdmin(boolean systemAdmin) { systemAdminHolder.set(systemAdmin); }
    public static boolean isSystemAdmin() {
        Boolean v = systemAdminHolder.get();
        return v != null && v;
    }

    /**
     * 全局视图标记（上帝视角）。仅由系统管理员通过 X-Team-ID: all 触发，
     * 用于跨团队查询绕过 team_id 过滤。
     */
    public static void setGlobalView(boolean globalView) { globalViewHolder.set(globalView); }
    public static boolean isGlobalView() {
        Boolean v = globalViewHolder.get();
        return v != null && v;
    }

    public static void clear() {
        tenantHolder.remove();
        userIdHolder.remove();
        systemAdminHolder.remove();
        globalViewHolder.remove();
    }
}
