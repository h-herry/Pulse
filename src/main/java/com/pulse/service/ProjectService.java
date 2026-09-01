package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.service.AuthorizationService;
import com.pulse.dto.ProjectRequest;
import com.pulse.entity.*;
import com.pulse.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目服务（R-025）。
 * 负责项目 CRUD、状态流转（暂停 / 恢复 / 删除）、归档与取消归档，
 * 并在创建项目时初始化任务号序列行与默认看板列。
 * 权限：创建 / 编辑 / 删除 / 状态变更 / 归档等写操作统一经 AuthorizationService.requireTeamLead
 * （队长或系统管理员）判定；所有写操作经 AuditService 落审计日志。
 */
@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final BoardColumnMapper boardColumnMapper;
    private final TaskSequenceMapper taskSequenceMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public ProjectService(ProjectMapper projectMapper, BoardColumnMapper boardColumnMapper,
                          TaskSequenceMapper taskSequenceMapper,
                          AuthorizationService authorizationService,
                          AuditService auditService) {
        this.projectMapper = projectMapper;
        this.boardColumnMapper = boardColumnMapper;
        this.taskSequenceMapper = taskSequenceMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /**
     * 创建项目（仅队长或系统管理员）。
     * 校验项目标识（projectKey）在团队内唯一；初始化任务号序列行并创建默认看板列；落审计。
     */
    @Transactional
    public Project createProject(ProjectRequest req) {
        // v7 修复：项目仅队长或系统管理员可创建（此前任何成员都能建，属越权漏洞）
        Long teamId = TenantContext.getTenantId();
        authorizationService.requireTeamLead(teamId);

        String projectKey = req.getProjectKey();
        if (projectKey == null || projectKey.trim().isEmpty()) {
            projectKey = generateUniqueProjectKey(req.getName(), teamId);
        }

        // Check project_key uniqueness within team
        boolean exists = projectMapper.exists(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .eq(Project::getProjectKey, projectKey));
        if (exists) {
            throw new BusinessException("该项目标识在当前团队已存在");
        }

        Project project = new Project();
        project.setTeamId(teamId);
        project.setName(req.getName());
        project.setProjectKey(projectKey);
        project.setDescription(req.getDescription());
        project.setStatus("active");
        project.setEnableReview(req.getEnableReview() != null && req.getEnableReview() ? 1 : 0);
        projectMapper.insert(project);

        // 即时为新项目写入任务号序列行：保证后续建任务热路径只做行锁读取，避免并发 INSERT 死锁
        try {
            taskSequenceMapper.insertSeq(project.getId(), 1);
        } catch (DuplicateKeyException e) {
            // 序列行已存在则忽略
        }

        // Create default board columns
        createDefaultColumns(project.getId(), teamId);

        auditService.record(AuditLog.Action.PROJECT_CREATE, teamId, AuditLog.Target.PROJECT, project.getId(),
                "创建项目：" + project.getName() + "（" + projectKey + "）");
        return project;
    }

    /**
     * 列出当前团队下的项目（排除已删除与已归档），按团队隔离。
     */
    public List<Project> listProjects() {
        Long teamId = TenantContext.getTenantId();
        return projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .ne(Project::getStatus, "deleted")
                        .ne(Project::getStatus, "archived"));
    }

    /**
     * 按 ID 获取项目，校验属于当前团队，否则抛「项目不存在」。
     */
    public Project getProject(Long id) {
        Project p = projectMapper.selectById(id);
        if (p == null || !p.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("项目不存在");
        }
        return p;
    }

    /**
     * 编辑项目（仅队长或系统管理员）；仅更新提供的字段并落审计。
     */
    public Project updateProject(Long id, ProjectRequest req) {
        // v7 修复：项目仅队长或系统管理员可编辑
        authorizationService.requireTeamLead(getProject(id).getTeamId());
        Project p = getProject(id);
        p.setName(req.getName());
        if (req.getProjectKey() != null) p.setProjectKey(req.getProjectKey());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getEnableReview() != null) p.setEnableReview(req.getEnableReview() ? 1 : 0);
        projectMapper.updateById(p);
        auditService.record(AuditLog.Action.PROJECT_UPDATE, p.getTeamId(), AuditLog.Target.PROJECT, p.getId(),
                "编辑项目：" + p.getName());
        return p;
    }

    /**
     * 变更项目状态（active / paused / deleted，仅队长或系统管理员），落审计。
     */
    public void updateProjectStatus(Long id, String status) {
        // v7 修复：暂停/恢复项目仅队长或系统管理员可操作
        authorizationService.requireTeamLead(getProject(id).getTeamId());
        Project p = getProject(id);
        if (!"active".equals(status) && !"paused".equals(status) && !"deleted".equals(status)) {
            throw new BusinessException("无效的项目状态: " + status);
        }
        p.setStatus(status);
        projectMapper.updateById(p);
        auditService.record(AuditLog.Action.PROJECT_STATUS, p.getTeamId(), AuditLog.Target.PROJECT, p.getId(),
                "项目状态变更为：" + status);
    }

    /**
     * 软删除项目（status=deleted，仅队长或系统管理员），落审计。
     */
    public void deleteProject(Long id) {
        // v7 修复：项目仅队长或系统管理员可删除
        authorizationService.requireTeamLead(getProject(id).getTeamId());
        Project p = getProject(id);
        p.setStatus("deleted");
        projectMapper.updateById(p);
        auditService.record(AuditLog.Action.PROJECT_DELETE, p.getTeamId(), AuditLog.Target.PROJECT, p.getId(),
                "删除（软删）项目：" + p.getName());
    }

    /**
     * 归档项目（R-029）：设置 status = 'archived'。
     * 校验 project 属于当前团队（getProject 内部基于 TenantContext）。
     */
    @Transactional
    public void archiveProject(Long id) {
        // v7 修复：归档/取消归档仅队长或系统管理员可操作
        authorizationService.requireTeamLead(getProject(id).getTeamId());
        Project p = getProject(id);
        p.setStatus("archived");
        projectMapper.updateById(p);
        auditService.record(AuditLog.Action.PROJECT_ARCHIVE, p.getTeamId(), AuditLog.Target.PROJECT, p.getId(),
                "归档项目：" + p.getName());
    }

    /**
     * 取消归档（R-029）：将 archived 状态恢复为 active。
     */
    @Transactional
    public void unarchiveProject(Long id) {
        authorizationService.requireTeamLead(getProject(id).getTeamId());
        Project p = getProject(id);
        if (!"archived".equals(p.getStatus())) {
            return;
        }
        p.setStatus("active");
        projectMapper.updateById(p);
        auditService.record(AuditLog.Action.PROJECT_UNARCHIVE, p.getTeamId(), AuditLog.Target.PROJECT, p.getId(),
                "取消归档项目：" + p.getName());
    }

    /**
     * 查询所有已归档项目（供 AdminDashboard 使用）。
     */
    public List<Project> listArchivedProjects() {
        return projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getStatus, "archived")
                        .orderByDesc(Project::getUpdatedAt));
    }

    private void createDefaultColumns(Long projectId, Long teamId) {
        String[][] defaults = {
                {"待办", "TODO", "0"},
                {"进行中", "IN_PROGRESS", "1"},
                {"评审", "REVIEW", "2"},
                {"已完成", "DONE", "3"}
        };
        for (String[] col : defaults) {
            BoardColumn bc = new BoardColumn();
            bc.setProjectId(projectId);
            bc.setTeamId(teamId);
            bc.setName(col[0]);
            bc.setMappedStatus(col[1]);
            bc.setSortOrder(Integer.parseInt(col[2]));
            bc.setWipLimit(null);
            boardColumnMapper.insert(bc);
        }
    }

    private String generateUniqueProjectKey(String name, Long teamId) {
        String base = generateBaseProjectKey(name);
        if (base.isEmpty()) base = "PROJ";
        String candidate = base.toUpperCase();
        int suffix = 1;
        while (projectMapper.exists(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .eq(Project::getProjectKey, candidate))) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate.length() > 10 ? candidate.substring(0, 10) : candidate;
    }

    private String generateBaseProjectKey(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("[\\s\\p{Punct}]", "");
        return cleaned.length() <= 3 ? cleaned : cleaned.substring(0, 3);
    }
}
