package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.Role;
import com.pulse.common.TenantContext;
import com.pulse.entity.TeamMember;
import com.pulse.mapper.TeamMemberMapper;
import org.springframework.stereotype.Service;

/**
 * 统一的团队内角色判定。替代 TeamService 中散落的 "ADMIN".equals(...) 字符串比较。
 *
 * 设计约束（见权限矩阵）：
 * - 系统管理员(is_system_admin)拥有全局权限，可执行任何团队内的 LEAD 操作；
 * - 团队队长(LEAD)仅在本团队内拥有管理权；
 * - 成员(MEMBER)仅执行任务，无管理权。
 */
@Service
public class AuthorizationService {

    private final TeamMemberMapper memberMapper;

    public AuthorizationService(TeamMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /** 当前登录用户是否为系统级管理员（跨团队）。 */
    public boolean isSystemAdmin() {
        return TenantContext.isSystemAdmin();
    }

    /** 当前是否处于全局视图（上帝视角）：系统管理员 + 已开启 GLOBAL Scope。 */
    public boolean isGodView() {
        return TenantContext.isSystemAdmin() && TenantContext.isGlobalView();
    }

    /** 当前登录用户是否为指定团队的队长(LEAD)。 */
    public boolean isTeamLead(Long teamId) {
        Long userId = TenantContext.getUserId();
        if (userId == null || teamId == null) return false;
        TeamMember self = memberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        return self != null && Role.LEAD.equals(self.getRole());
    }

    /**
     * 要求当前用户具备团队管理权：系统管理员 或 本团队队长。
     * 不满足则抛出 BusinessException("需要管理员权限")。
     */
    public void requireTeamLead(Long teamId) {
        if (TenantContext.isSystemAdmin()) return;
        if (!isTeamLead(teamId)) {
            throw new BusinessException("需要管理员权限");
        }
    }

    /**
     * 判断当前用户是否为指定团队的最后一名队长。
     * 用于「降级/移除最后一名队长」的防护（沿用 v6 P3-3 逻辑，适配 LEAD 角色）。
     */
    public boolean isLastTeamLead(Long teamId, Long excludeUserId) {
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getRole, Role.LEAD));
        if (count == null) return false;
        // 若排除的这位本身就是队长，则其离开后剩余队长数 = count - 1
        boolean excludingIsLead = isTeamLeadFor(teamId, excludeUserId);
        long remaining = excludingIsLead ? count - 1 : count;
        return remaining <= 0;
    }

    private boolean isTeamLeadFor(Long teamId, Long userId) {
        if (userId == null) return false;
        TeamMember m = memberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        return m != null && Role.LEAD.equals(m.getRole());
    }
}
