package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.BusinessException;
import com.pulse.dto.SavedViewRequest;
import com.pulse.entity.Project;
import com.pulse.entity.SavedView;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.SavedViewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 保存的筛选视图服务（R-020）
 *
 * 所有方法均校验 project 属于当前团队（TenantContext 注入的 teamId）。
 */
@Service
public class SavedViewService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SavedViewMapper savedViewMapper;
    private final ProjectMapper projectMapper;

    public SavedViewService(SavedViewMapper savedViewMapper, ProjectMapper projectMapper) {
        this.savedViewMapper = savedViewMapper;
        this.projectMapper = projectMapper;
    }

    private void verifyProject(Long projectId, Long teamId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null || !p.getTeamId().equals(teamId)) {
            throw new BusinessException("项目不存在");
        }
    }

    public List<SavedView> list(Long projectId, Long teamId, Long userId) {
        verifyProject(projectId, teamId);
        return savedViewMapper.selectList(new LambdaQueryWrapper<SavedView>()
                .eq(SavedView::getProjectId, projectId)
                .eq(SavedView::getTeamId, teamId)
                .eq(SavedView::getUserId, userId)
                .orderByAsc(SavedView::getId));
    }

    @Transactional
    public SavedView create(Long projectId, SavedViewRequest req, Long teamId, Long userId) {
        verifyProject(projectId, teamId);
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException("视图名称不能为空");
        }
        SavedView v = new SavedView();
        v.setProjectId(projectId);
        v.setTeamId(teamId);
        v.setUserId(userId);
        v.setName(req.getName().trim());
        v.setFilters(toJson(req.getFilters()));
        v.setSortConfig(toJson(req.getSortConfig()));
        savedViewMapper.insert(v);
        return v;
    }

    @Transactional
    public SavedView update(Long id, SavedViewRequest req, Long teamId) {
        SavedView v = savedViewMapper.selectById(id);
        if (v == null || !v.getTeamId().equals(teamId)) {
            throw new BusinessException("保存的视图不存在");
        }
        if (req.getName() != null) v.setName(req.getName().trim());
        if (req.getFilters() != null) v.setFilters(toJson(req.getFilters()));
        if (req.getSortConfig() != null) v.setSortConfig(toJson(req.getSortConfig()));
        savedViewMapper.updateById(v);
        return v;
    }

    @Transactional
    public void delete(Long id, Long teamId) {
        SavedView v = savedViewMapper.selectById(id);
        if (v == null || !v.getTeamId().equals(teamId)) {
            throw new BusinessException("保存的视图不存在");
        }
        savedViewMapper.deleteById(id);
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
