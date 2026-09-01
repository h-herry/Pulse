package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pulse.common.BusinessException;
import com.pulse.common.Role;
import com.pulse.common.TenantContext;
import com.pulse.entity.AuditLog;
import com.pulse.entity.Project;
import com.pulse.entity.Task;
import com.pulse.entity.Team;
import com.pulse.entity.TeamMember;
import com.pulse.entity.User;
import com.pulse.mapper.AuditLogMapper;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TaskMapper;
import com.pulse.mapper.TeamMapper;
import com.pulse.mapper.TeamMemberMapper;
import com.pulse.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 系统级管理后台服务（上帝视角，跨团队全局操作）。
 * 提供团队 / 项目 / 用户 / 成员的全局管理，以及跨团队任务查看与审计日志查询。
 * 权限：所有写操作经 verifyGlobalAdmin() 校验 users.is_system_admin=true（系统管理员，
 * 与团队内队长 role 解耦）；所有敏感操作经 AuditService 落审计日志。
 * 关键约束：禁止将唯一系统管理员降级、禁止管理员禁用自己、移动成员时保护源团队最后一名队长。
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;
    private final AuthService authService;

    public AdminService(TeamMapper teamMapper, TeamMemberMapper teamMemberMapper,
                        ProjectMapper projectMapper, TaskMapper taskMapper,
                        UserMapper userMapper, PasswordEncoder passwordEncoder,
                        AuditService auditService, AuditLogMapper auditLogMapper,
                        AuthService authService) {
        this.teamMapper = teamMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.auditLogMapper = auditLogMapper;
        this.authService = authService;
    }

    public List<Map<String, Object>> getAllTeams() {
        List<Team> teams = teamMapper.selectList(
                new LambdaQueryWrapper<Team>().orderByDesc(Team::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Team team : teams) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", team.getId());
            m.put("name", team.getName());
            m.put("description", team.getDescription());
            m.put("createdAt", team.getCreatedAt());

            Long memberCount = teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, team.getId()));
            m.put("memberCount", memberCount);

            Long projectCount = projectMapper.selectCount(
                    new LambdaQueryWrapper<Project>()
                            .eq(Project::getTeamId, team.getId())
                            .ne(Project::getStatus, "deleted"));
            m.put("projectCount", projectCount);

            // Task count: count tasks across all non-deleted projects in this team
            List<Project> projects = projectMapper.selectList(
                    new LambdaQueryWrapper<Project>()
                            .eq(Project::getTeamId, team.getId())
                            .ne(Project::getStatus, "deleted"));
            long taskCount = 0;
            for (Project p : projects) {
                Long c = taskMapper.selectCount(
                        new LambdaQueryWrapper<com.pulse.entity.Task>()
                                .eq(com.pulse.entity.Task::getProjectId, p.getId()));
                taskCount += c;
            }
            m.put("taskCount", taskCount);

            result.add(m);
        }
        return result;
    }

    public List<Map<String, Object>> getAllProjects() {
        List<Team> teams = teamMapper.selectList(null);
        Map<Long, Team> teamMap = new HashMap<>();
        for (Team t : teams) teamMap.put(t.getId(), t);

        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .ne(Project::getStatus, "deleted")
                        .orderByDesc(Project::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("teamId", p.getTeamId());
            m.put("name", p.getName());
            m.put("projectKey", p.getProjectKey());
            m.put("description", p.getDescription());
            m.put("status", p.getStatus());
            m.put("enableReview", p.getEnableReview());
            m.put("createdAt", p.getCreatedAt());
            m.put("teamName", teamMap.getOrDefault(p.getTeamId(), new Team()).getName());
            result.add(m);
        }
        return result;
    }

    /**
     * 纵深防御（v7 适配）：AdminController 类级已有 @PreAuthorize("hasRole('ADMIN')")，
     * 但 Service 层不应仅依赖控制器注解。此处校验当前登录用户为系统级管理员
     * （依据 users.is_system_admin，与 JwtAuthFilter 的全局管理员判定一致）。
     */
    private void verifyGlobalAdmin() {
        if (!TenantContext.isSystemAdmin()) {
            throw new BusinessException("需要管理员权限");
        }
    }

    public List<Map<String, Object>> getTeamProjects(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        String teamName = team != null ? team.getName() : null;

        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .ne(Project::getStatus, "deleted")
                        .orderByDesc(Project::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("teamId", p.getTeamId());
            m.put("name", p.getName());
            m.put("projectKey", p.getProjectKey());
            m.put("description", p.getDescription());
            m.put("status", p.getStatus());
            m.put("enableReview", p.getEnableReview());
            m.put("createdAt", p.getCreatedAt());
            m.put("teamName", teamName);
            result.add(m);
        }
        return result;
    }

    /**
     * 获取所有已归档项目（R-029），供管理后台「归档项目管理」使用。
     */
    public List<Map<String, Object>> listArchivedProjects() {
        List<Team> teams = teamMapper.selectList(null);
        Map<Long, Team> teamMap = new HashMap<>();
        for (Team t : teams) teamMap.put(t.getId(), t);

        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getStatus, "archived")
                        .orderByDesc(Project::getUpdatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("teamId", p.getTeamId());
            m.put("name", p.getName());
            m.put("projectKey", p.getProjectKey());
            m.put("description", p.getDescription());
            m.put("status", p.getStatus());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            m.put("teamName", teamMap.getOrDefault(p.getTeamId(), new Team()).getName());
            result.add(m);
        }
        return result;
    }

    // ===== R-014: 用户管理 =====

    /**
     * 获取所有用户（含启用状态、是否系统管理员），按创建时间倒序。
     * 回填 transient 字段 isAdmin，供前端管理后台判定全局角色。
     */
    public List<User> listUsers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt));
        for (User u : users) {
            u.setIsAdmin(u.getIsSystemAdmin() != null && u.getIsSystemAdmin());
        }
        return users;
    }

    /**
     * 设置用户全局角色（管理员 / 普通用户）。
     * role 取值：admin=系统管理员（is_system_admin=1），user=普通用户（is_system_admin=0）。
     * 防御：禁止将唯一系统管理员降级为普通用户，避免系统失去所有管理员。
     */
    @Transactional
    public void setUserAdmin(Long userId, boolean admin) {
        verifyGlobalAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!admin && Boolean.TRUE.equals(user.getIsSystemAdmin())) {
            Long adminCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getIsSystemAdmin, true));
            if (adminCount != null && adminCount <= 1) {
                throw new BusinessException("至少需保留一名系统管理员");
            }
        }
        user.setIsSystemAdmin(admin);
        userMapper.updateById(user);
        auditService.record(AuditLog.Action.ADMIN_USER_ROLE, user.getId(), AuditLog.Target.USER, userId,
                "全局角色变更：" + (admin ? "管理员" : "普通用户"));
    }

    /**
     * 启用 / 禁用用户。
     * 不允许管理员禁用自己，避免把自己锁在系统外。
     */
    public void toggleUserEnabled(Long userId, boolean enabled) {
        verifyGlobalAdmin();
        Long current = TenantContext.getUserId();
        // 仅禁止「禁用自己」，避免管理员把自己锁在系统外；启用自己为安全空操作
        if (!enabled && current != null && current.equals(userId)) {
            throw new BusinessException("不能禁用自己");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setEnabled(enabled);
        // 令牌代际号自增：禁用/启用后目标用户此前签发的 refresh token 在下次刷新时被拒（凭据吊销）
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(user);
        log.info("[AUDIT] admin={} toggleUserEnabled userId={} enabled={}", current, userId, enabled);
        auditService.record(enabled ? AuditLog.Action.ADMIN_USER_ENABLE : AuditLog.Action.ADMIN_USER_DISABLE,
                null, AuditLog.Target.USER, userId,
                "管理员" + (enabled ? "启用" : "禁用") + "用户：" + user.getEmail());
    }

    /**
     * 重置用户密码：改为签发一次性重置令牌返回（不再返回明文临时密码，避免凭据经响应体泄露）。
     */
    public Map<String, String> resetPassword(Long userId) {
        verifyGlobalAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 委派 AuthService 生成一次性重置令牌，避免明文凭据经响应体泄露
        Map<String, String> result = authService.resetPasswordForAdmin(userId);
        // 审计：只记录操作者与目标，绝不记录明文临时密码
        log.info("[AUDIT] admin={} resetPassword userId={}", TenantContext.getUserId(), userId);
        auditService.record(AuditLog.Action.ADMIN_RESET_PASSWORD, null, AuditLog.Target.USER, userId,
                "重置用户密码：" + user.getEmail());
        return result;
    }

    /**
     * 更新用户显示名称（v6：控制器已用 @Valid AdminUpdateProfileRequest 保证非空且 ≤100，
     * 此处再做一次去空白与非空兜底，纵深防御）。
     */
    public User updateUserProfile(Long userId, String displayName) {
        verifyGlobalAdmin();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new BusinessException("显示名称不能为空");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 100) {
            throw new BusinessException("显示名称最多 100 字符");
        }
        user.setDisplayName(trimmed);
        userMapper.updateById(user);
        log.info("[AUDIT] admin={} updateUserProfile userId={}", TenantContext.getUserId(), userId);
        auditService.record(AuditLog.Action.ADMIN_USER_PROFILE, null, AuditLog.Target.USER, userId,
                "更新用户资料：" + user.getEmail() + " → 显示名=" + trimmed);
        return user;
    }

    /**
     * 获取所有成员（含团队信息），按团队分组展示用。
     */
    public List<Map<String, Object>> getAllMembers() {
        List<TeamMember> members = teamMemberMapper.selectList(null);
        List<Team> teams = teamMapper.selectList(null);
        Map<Long, Team> teamMap = new HashMap<>();
        for (Team t : teams) teamMap.put(t.getId(), t);

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMember tm : members) {
            User user = userMapper.selectById(tm.getUserId());
            if (user == null) continue;
            Team team = teamMap.get(tm.getTeamId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", tm.getUserId());
            m.put("teamId", tm.getTeamId());
            m.put("teamName", team != null ? team.getName() : null);
            m.put("displayName", user.getDisplayName());
            m.put("email", user.getEmail());
            m.put("avatarUrl", user.getAvatarUrl());
            m.put("role", tm.getRole());
            m.put("joinedAt", tm.getJoinedAt());
            result.add(m);
        }
        return result;
    }

    /**
     * 修改成员所属团队。仅移动指定的 fromTeamId 关系至 toTeamId。
     */
    @Transactional
    public void moveMemberTeam(Long userId, Long fromTeamId, Long toTeamId) {
        verifyGlobalAdmin();
        if (fromTeamId.equals(toTeamId)) return;

        Team team = teamMapper.selectById(toTeamId);
        if (team == null) throw new BusinessException("目标团队不存在");

        TeamMember source = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getTeamId, fromTeamId));
        if (source == null) throw new BusinessException("成员不在源团队中");

        // v7 适配：若该成员是源团队最后一名队长(LEAD)，移出后团队将失去管理员，拒绝该操作
        if (Role.LEAD.equals(source.getRole())) {
            Long leadCount = teamMemberMapper.selectCount(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, fromTeamId)
                            .eq(TeamMember::getRole, Role.LEAD));
            if (leadCount != null && leadCount <= 1) {
                throw new BusinessException("该成员是源团队唯一队长，无法移出；请先为源团队指定其他队长");
            }
        }

        TeamMember currentInTarget = teamMemberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getTeamId, toTeamId));
        if (currentInTarget != null) {
            // 目标团队已存在该成员，仅删除源团队关系
            teamMemberMapper.delete(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getUserId, userId)
                            .eq(TeamMember::getTeamId, fromTeamId));
            return;
        }

        // 删除源团队关系并加入新团队
        teamMemberMapper.delete(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getTeamId, fromTeamId));

        TeamMember tm = new TeamMember();
        tm.setTeamId(toTeamId);
        tm.setUserId(userId);
        tm.setRole(source.getRole());
        teamMemberMapper.insert(tm);
        log.info("[AUDIT] admin={} moveMemberTeam userId={} from={} to={}",
                TenantContext.getUserId(), userId, fromTeamId, toTeamId);
        auditService.record(AuditLog.Action.ADMIN_MOVE_MEMBER_TEAM, toTeamId, AuditLog.Target.MEMBER, userId,
                "移动成员所属团队：userId=" + userId + "，从团队" + fromTeamId + "到团队" + toTeamId);
    }

    // ===== v7 增量：操作审计日志查询（供管理后台展示） =====

    /**
     * 查询审计日志（倒序），支持按团队与动作类型过滤，分页由 limit/offset 控制。
     * 返回前会在内存中解析目标名称（团队/项目/用户），便于前端直接展示。
     */
    public List<Map<String, Object>> listAuditLogs(Long teamId, String action, int limit, int offset) {
        LambdaQueryWrapper<AuditLog> q = new LambdaQueryWrapper<>();
        if (teamId != null) q.eq(AuditLog::getTeamId, teamId);
        if (action != null && !action.isBlank()) q.eq(AuditLog::getAction, action);
        q.orderByDesc(AuditLog::getCreatedAt)
         .last("LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset));
        List<AuditLog> logs = auditLogMapper.selectList(q);

        List<Map<String, Object>> result = new ArrayList<>();
        for (AuditLog log : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", log.getId());
            m.put("createdAt", log.getCreatedAt());
            m.put("actorUserId", log.getActorUserId());
            m.put("actorName", log.getActorName());
            m.put("teamId", log.getTeamId());
            m.put("teamName", log.getTeamId() != null ? resolveTeamName(log.getTeamId()) : null);
            m.put("action", log.getAction());
            m.put("targetType", log.getTargetType());
            m.put("targetId", log.getTargetId());
            m.put("targetLabel", resolveTargetLabel(log.getTargetType(), log.getTargetId()));
            m.put("detail", log.getDetail());
            result.add(m);
        }
        return result;
    }

    /**
     * 返回去重后的动作类型列表（升序），供前端筛选下拉使用。
     */
    public List<String> listAuditActions() {
        List<AuditLog> distinct = auditLogMapper.selectList(
                new LambdaQueryWrapper<AuditLog>()
                        .select(AuditLog::getAction)
                        .groupBy(AuditLog::getAction)
                        .orderByAsc(AuditLog::getAction));
        List<String> actions = new ArrayList<>();
        for (AuditLog l : distinct) {
            if (l.getAction() != null && !actions.contains(l.getAction())) {
                actions.add(l.getAction());
            }
        }
        return actions;
    }

    private String resolveTeamName(Long teamId) {
        Team t = teamMapper.selectById(teamId);
        return t != null ? t.getName() : null;
    }

    /**
     * 上帝视角：系统管理员跨团队查看全部任务。
     * 返回分页结果，并为每条任务 enrichment 团队名 / 项目名 / 指派人姓名。
     * 该接口置于 /api/v1/admin 下，已绕过团队上下文校验，天然为全局范围（不走 team_id 过滤）。
     */
    public Map<String, Object> listAllTasks(int page, int size, String keyword, String status) {
        LambdaQueryWrapper<Task> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Task::getTitle, keyword);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Task::getStatus, status);
        }
        w.orderByDesc(Task::getUpdatedAt);

        IPage<Task> ip = new Page<>(page, size);
        taskMapper.selectPage(ip, w);

        // 批量预取关联名，避免 N+1
        Set<Long> teamIds = new HashSet<>();
        Set<Long> projectIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Task t : ip.getRecords()) {
            if (t.getTeamId() != null) teamIds.add(t.getTeamId());
            if (t.getProjectId() != null) projectIds.add(t.getProjectId());
            if (t.getAssigneeId() != null) userIds.add(t.getAssigneeId());
        }
        Map<Long, String> teamNames = new HashMap<>();
        for (Long id : teamIds) teamNames.put(id, resolveTeamName(id));
        Map<Long, String> projectNames = new HashMap<>();
        for (Long id : projectIds) {
            Project p = projectMapper.selectById(id);
            projectNames.put(id, p != null ? p.getName() : null);
        }
        Map<Long, String> userNames = new HashMap<>();
        for (Long id : userIds) {
            User u = userMapper.selectById(id);
            userNames.put(id, u != null ? u.getDisplayName() : null);
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (Task t : ip.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("teamId", t.getTeamId());
            m.put("teamName", teamNames.get(t.getTeamId()));
            m.put("projectId", t.getProjectId());
            m.put("projectName", projectNames.get(t.getProjectId()));
            m.put("assigneeId", t.getAssigneeId());
            m.put("assigneeName", userNames.get(t.getAssigneeId()));
            m.put("updatedAt", t.getUpdatedAt());
            records.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", ip.getTotal());
        result.put("pages", ip.getPages());
        result.put("current", ip.getCurrent());
        result.put("size", ip.getSize());
        return result;
    }

    private String resolveTargetLabel(String targetType, Long targetId) {
        if (targetId == null) return null;
        if (AuditLog.Target.TEAM.equals(targetType)) {
            Team t = teamMapper.selectById(targetId);
            return t != null ? t.getName() : ("团队#" + targetId);
        } else if (AuditLog.Target.PROJECT.equals(targetType)) {
            Project p = projectMapper.selectById(targetId);
            return p != null ? p.getName() : ("项目#" + targetId);
        } else if (AuditLog.Target.USER.equals(targetType) || AuditLog.Target.MEMBER.equals(targetType)) {
            User u = userMapper.selectById(targetId);
            return u != null ? (u.getDisplayName() + "（" + u.getEmail() + "）") : ("用户#" + targetId);
        }
        return null;
    }
}
