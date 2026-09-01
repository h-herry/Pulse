package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.dto.ColumnRequest;
import com.pulse.entity.BoardColumn;
import com.pulse.mapper.BoardColumnMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 看板列（Board Column）服务（R-022）。
 * 负责看板列的查询、创建、更新、删除与批量重排，列与项目、团队强绑定。
 * 安全：所有操作基于 TenantContext 的 teamId 校验团队归属；列顺序以 sortOrder 维护，
 * 删除 / 更新前均校验列属于当前团队。
 */
@Service
public class BoardColumnService {

    private final BoardColumnMapper boardColumnMapper;
    private final AuthorizationService authorizationService;

    public BoardColumnService(BoardColumnMapper boardColumnMapper,
                              AuthorizationService authorizationService) {
        this.boardColumnMapper = boardColumnMapper;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询项目的全部看板列（按 sortOrder 升序）。
     * 依赖 TenantContext 的 teamId 进行团队隔离。
     */
    public List<BoardColumn> getColumns(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        return boardColumnMapper.selectList(
                new LambdaQueryWrapper<BoardColumn>()
                        .eq(BoardColumn::getProjectId, projectId)
                        .eq(BoardColumn::getTeamId, teamId)
                        .orderByAsc(BoardColumn::getSortOrder));
    }

    /**
     * 在项目下新建看板列，自动追加到列尾（sortOrder = 当前列数）。
     * 团队归属取自 TenantContext；映射状态与 WIP 限制来自请求体。
     */
    public BoardColumn createColumn(Long projectId, ColumnRequest req) {
        Long teamId = TenantContext.getTenantId();
        BoardColumn bc = new BoardColumn();
        bc.setProjectId(projectId);
        bc.setTeamId(teamId);
        bc.setName(req.getName());
        bc.setMappedStatus(req.getMappedStatus());
        bc.setWipLimit(req.getWipLimit());

        Long count = boardColumnMapper.selectCount(
                new LambdaQueryWrapper<BoardColumn>()
                        .eq(BoardColumn::getProjectId, projectId));
        bc.setSortOrder(count.intValue());
        boardColumnMapper.insert(bc);
        return bc;
    }

    /**
     * 更新看板列的名称 / 映射状态 / WIP 限制（仅提供字段才更新）。
     * 校验列属于当前团队，否则视为不存在。
     */
    public BoardColumn updateColumn(Long id, ColumnRequest req) {
        BoardColumn bc = boardColumnMapper.selectById(id);
        if (bc == null || !bc.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("列不存在");
        }
        bc.setName(req.getName());
        if (req.getWipLimit() != null) bc.setWipLimit(req.getWipLimit());
        if (req.getMappedStatus() != null) bc.setMappedStatus(req.getMappedStatus());
        boardColumnMapper.updateById(bc);
        return bc;
    }

    /**
     * 删除看板列。校验列属于当前团队，否则视为不存在。
     */
    public void deleteColumn(Long id) {
        BoardColumn bc = boardColumnMapper.selectById(id);
        if (bc == null || !bc.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("列不存在");
        }
        // 破坏性删除：仅团队 LEAD 或系统管理员
        authorizationService.requireTeamLead(TenantContext.getTenantId());
        boardColumnMapper.deleteById(id);
    }

    /**
     * 批量重排列顺序。
     *
     * @param projectId 项目 ID
     * @param columnIds 按目标顺序排列的列 ID 列表
     */
    @org.springframework.transaction.annotation.Transactional
    public void reorderColumns(Long projectId, List<Long> columnIds) {
        Long teamId = TenantContext.getTenantId();
        if (columnIds == null || columnIds.isEmpty()) return;
        for (int i = 0; i < columnIds.size(); i++) {
            Long colId = columnIds.get(i);
            BoardColumn bc = boardColumnMapper.selectById(colId);
            if (bc == null || !bc.getProjectId().equals(projectId) || !bc.getTeamId().equals(teamId)) {
                throw new BusinessException("列不存在");
            }
            bc.setSortOrder(i);
            boardColumnMapper.updateById(bc);
        }
    }
}
