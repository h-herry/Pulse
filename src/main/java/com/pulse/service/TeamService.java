package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.Role;
import com.pulse.common.TenantContext;
import com.pulse.dto.MemberVO;
import com.pulse.dto.AddMemberRequest;
import com.pulse.dto.AddMemberResult;
import com.pulse.dto.TeamRequest;
import com.pulse.entity.*;
import com.pulse.mapper.*;
import com.pulse.security.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 团队与成员服务（团队内管理权核心）。
 *
 * <p>职责：团队 CRUD、成员增删与角色变更、成员列表、团队下项目进度聚合。
 * 权限统一经由 {@code AuthorizationService} 判定（队长或系统管理员），并落实
 * 「最后一名队长保护」；所有敏感操作经 {@code AuditService} 落审计日志。
 */
@Service
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final JwtUtils jwtUtils;

    public TeamService(TeamMapper teamMapper, TeamMemberMapper memberMapper,
                       UserMapper userMapper, ProjectMapper projectMapper,
                       TaskMapper taskMapper,
                       PasswordEncoder passwordEncoder,
                       AuthorizationService authorizationService,
                       AuditService auditService,
                       JwtUtils jwtUtils) {
        this.teamMapper = teamMapper;
        this.memberMapper = memberMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.jwtUtils = jwtUtils;
    }

    @Transactional
    public Team createTeam(TeamRequest req) {
        Long userId = TenantContext.getUserId();

        Team team = new Team();
        team.setName(req.getName());
        team.setDescription(req.getDescription());
        teamMapper.insert(team);

        TeamMember member = new TeamMember();
        member.setTeamId(team.getId());
        member.setUserId(userId);
        member.setRole(Role.LEAD);
        memberMapper.insert(member);

        // 审计：团队创建（闭合审计链路，此前 createTeam 未落审计）
        auditService.record(AuditLog.Action.TEAM_CREATE, team.getId(), AuditLog.Target.TEAM, team.getId(),
                "创建团队：" + team.getName());

        return team;
    }

    public Team getTeam(Long id) {
        if (!TenantContext.isSystemAdmin() && !id.equals(TenantContext.getTenantId()))
            throw new BusinessException("无权访问");
        Team team = teamMapper.selectById(id);
        if (team == null) throw new BusinessException("团队不存在");
        return team;
    }

    public Team updateTeam(Long id, TeamRequest req) {
        // v7 修复：团队设置仅队长或系统管理员可修改（此前任何成员都能改，属越权漏洞）
        authorizationService.requireTeamLead(id);
        Team team = getTeam(id);
        if (req.getName() != null) team.setName(req.getName());
        if (req.getDescription() != null) team.setDescription(req.getDescription());
        teamMapper.updateById(team);
        auditService.record(AuditLog.Action.TEAM_UPDATE, id, AuditLog.Target.TEAM, id,
                "更新团队设置" + (req.getName() != null ? "：名称=" + req.getName() : ""));
        return team;
    }

    public void deleteTeam(Long id) {
        authorizationService.requireTeamLead(id);
        String teamName = getTeam(id).getName();
        teamMapper.deleteById(id);
        auditService.record(AuditLog.Action.TEAM_DELETE, id, AuditLog.Target.TEAM, id, "删除团队：" + teamName);
    }

    public List<MemberVO> listMembers(Long teamId) {
        List<TeamMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        List<MemberVO> result = new ArrayList<>();
        for (TeamMember m : members) {
            User user = userMapper.selectById(m.getUserId());
            // 系统管理员与团队角色解耦，不在团队成员列表中展示（上帝视角，隐藏于普通成员管理）。
            if (user != null && Boolean.TRUE.equals(user.getIsSystemAdmin())) {
                continue;
            }
            MemberVO vo = new MemberVO();
            vo.setUserId(m.getUserId());
            vo.setDisplayName(user != null ? user.getDisplayName() : null);
            vo.setEmail(user != null ? user.getEmail() : null);
            vo.setRole(m.getRole());
            vo.setJoinedAt(m.getJoinedAt());
            result.add(vo);
        }
        return result;
    }

    public void updateMemberRole(Long teamId, Long userId, String role) {
        authorizationService.requireTeamLead(teamId);
        String normalized = normalizeRole(role);
        TeamMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        if (member == null) throw new BusinessException("成员不存在");
        // v7 修复 P3-3 适配：禁止把团队最后一名队长(LEAD)降级/移除
        if (Role.LEAD.equals(member.getRole()) && !Role.LEAD.equals(normalized)) {
            if (authorizationService.isLastTeamLead(teamId, userId)) {
                throw new BusinessException("不能移除团队唯一队长，请先指定其他队长");
            }
        }
        member.setRole(normalized);
        memberMapper.updateById(member);
        auditService.record(AuditLog.Action.TEAM_MEMBER_ROLE_CHANGE, teamId, AuditLog.Target.MEMBER, userId,
                "角色变更：" + member.getRole() + " → " + normalized);
    }

    /**
     * v6 修复 P3-1/P3-2：角色白名单校验。role 直接落库前必须限定在 {LEAD, USER}，
     * 防止写入任意字符串（如 "SUPER"）绕过后续基于角色的权限判定。
     */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessException("角色不能为空");
        }
        String r = role.trim().toUpperCase();
        if (!Role.LEAD.equals(r) && !Role.USER.equals(r)) {
            throw new BusinessException("非法角色：" + role);
        }
        return r;
    }

    public void removeMember(Long teamId, Long userId) {
        authorizationService.requireTeamLead(teamId);
        if (userId.equals(TenantContext.getUserId())) {
            throw new BusinessException("不能移除自己");
        }
        memberMapper.delete(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, userId));
        auditService.record(AuditLog.Action.TEAM_MEMBER_REMOVE, teamId, AuditLog.Target.MEMBER, userId, "移除成员");
    }

    @Transactional
    public AddMemberResult addMember(Long teamId, AddMemberRequest req) {
        authorizationService.requireTeamLead(teamId);

        String email = req.getEmail();
        if (email == null || email.isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }
        email = email.trim();

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        String initialPassword = null;
        if (user == null) {
            // 邮箱尚未注册：管理员直接为其创建账号
            user = new User();
            user.setEmail(email);
            String name = (req.getDisplayName() != null && !req.getDisplayName().isBlank())
                    ? req.getDisplayName().trim() : email.split("@")[0];
            user.setDisplayName(name);
            initialPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            user.setPasswordHash(passwordEncoder.encode(initialPassword));
            // 新账号初始密码为系统生成，强制首次登录改密
            user.setMustChangePassword(true);
            userMapper.insert(user);
        } else {
            boolean already = memberMapper.exists(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, teamId)
                            .eq(TeamMember::getUserId, user.getId()));
            if (already) {
                throw new BusinessException("该用户已是团队成员");
            }
        }

        String role = (req.getRole() != null && !req.getRole().isBlank()) ? normalizeRole(req.getRole()) : Role.USER;
        TeamMember tm = new TeamMember();
        tm.setTeamId(teamId);
        tm.setUserId(user.getId());
        tm.setRole(role);
        memberMapper.insert(tm);
        auditService.record(AuditLog.Action.TEAM_MEMBER_ADD, teamId, AuditLog.Target.MEMBER, user.getId(),
                "添加成员：" + email + "，角色=" + role);

        MemberVO vo = new MemberVO();
        vo.setUserId(user.getId());
        vo.setDisplayName(user.getDisplayName());
        vo.setEmail(user.getEmail());
        vo.setRole(role);
        vo.setJoinedAt(LocalDateTime.now());

        AddMemberResult result = new AddMemberResult();
        result.setMember(vo);
        // 新邮箱自动建号：不再返回明文初始密码，改为签发一次性重置令牌，由队长转发链接自助设密
        if (initialPassword != null) {
            result.setResetToken(jwtUtils.generatePasswordResetToken(user.getId()));
            result.setResetUrl("/reset-password?token=" + result.getResetToken());
        }
        return result;
    }

    public List<Team> listMyTeams() {
        // 系统管理员拥有上帝视角：返回全部团队，便于在切换器中跨团队导航。
        if (TenantContext.isSystemAdmin()) {
            return teamMapper.selectList(
                    new LambdaQueryWrapper<Team>().orderByDesc(Team::getCreatedAt));
        }
        Long userId = TenantContext.getUserId();
        List<TeamMember> myMembers = memberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getUserId, userId));
        if (myMembers.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> teamIds = new ArrayList<>();
        for (TeamMember m : myMembers) {
            teamIds.add(m.getTeamId());
        }
        return teamMapper.selectList(new LambdaQueryWrapper<Team>().in(Team::getId, teamIds));
    }

    /**
     * 批量查询团队下所有项目的进度（一次查询，避免 N+1）。
     *
     * @param teamId 团队 ID
     * @return 项目进度列表 [{ projectId, projectName, projectKey, taskTotal, taskDone, progress }]
     */
    public List<Map<String, Object>> getProjectsProgress(Long teamId) {
        // 查询团队下所有项目
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId));
        if (projects.isEmpty()) return new ArrayList<>();

        // 批量查询团队下所有非子任务，按 projectId 分组
        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTeamId, teamId)
                        .isNull(Task::getParentId));
        Map<Long, List<Task>> byProject = new HashMap<>();
        for (Task t : allTasks) {
            byProject.computeIfAbsent(t.getProjectId(), k -> new ArrayList<>()).add(t);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            List<Task> tasks = byProject.getOrDefault(p.getId(), new ArrayList<>());
            int total = tasks.size();
            int done = 0;
            for (Task t : tasks) {
                if ("DONE".equals(t.getStatus())) done++;
            }
            int progress = total > 0 ? Math.round((float) done / total * 100) : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", p.getId());
            m.put("projectName", p.getName());
            m.put("projectKey", p.getProjectKey());
            m.put("taskTotal", total);
            m.put("taskDone", done);
            m.put("progress", progress);
            result.add(m);
        }
        return result;
    }
}
