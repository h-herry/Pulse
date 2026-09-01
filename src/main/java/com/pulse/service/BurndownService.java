package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.TenantContext;
import com.pulse.entity.BurnDownSnapshot;
import com.pulse.entity.Task;
import com.pulse.mapper.BurnDownSnapshotMapper;
import com.pulse.mapper.TaskMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 燃尽图（Burndown）快照服务。
 * 按「项目 + 团队 + 日期」记录每日任务剩余量快照，供迭代 / 项目趋势报表使用。
 * 采用并发安全的 upsert 语义，同一天只保留一个快照点（详见 takeSnapshot）。
 */
@Service
public class BurndownService {

    private final BurnDownSnapshotMapper snapshotMapper;
    private final TaskMapper taskMapper;

    public BurndownService(BurnDownSnapshotMapper snapshotMapper, TaskMapper taskMapper) {
        this.snapshotMapper = snapshotMapper;
        this.taskMapper = taskMapper;
    }

    /**
     * 记录一次燃尽图快照。
     * 采用 upsert 语义：同一项目、同一团队、同一天只保留一个快照点，
     * 始终反映最新状态。这样既支持每日定时生成，也支持任务流转时的实时触发，
     * 不会出现同一天重复堆积多条相同数据。
     */
    /**
     * 记录燃尽图快照（并发安全）。
     * 同一项目/团队/日期只保留一个快照点：用 selectList 读取（避免 selectOne 在并发重复行下抛 TooManyResults），
     * 不存在则插入；插入遇唯一约束冲突（并发下另一事务已写入）则回退为更新；
     * 若仍出现瞬态重复行则更新首条并清理其余，自愈数据。
     */
    public void takeSnapshot(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        LocalDate today = LocalDate.now();
        Long totalCount = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId));
        Long remainingCount = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId)
                        .ne(Task::getStatus, "DONE"));

        List<BurnDownSnapshot> list = snapshotMapper.selectList(
                new LambdaQueryWrapper<BurnDownSnapshot>()
                        .eq(BurnDownSnapshot::getProjectId, projectId)
                        .eq(BurnDownSnapshot::getTeamId, teamId)
                        .eq(BurnDownSnapshot::getSnapshotDate, today));

        if (list.isEmpty()) {
            BurnDownSnapshot snapshot = new BurnDownSnapshot();
            snapshot.setProjectId(projectId);
            snapshot.setTeamId(teamId);
            snapshot.setSnapshotDate(today);
            snapshot.setRemainingCount(remainingCount.intValue());
            snapshot.setTotalCount(totalCount.intValue());
            try {
                snapshotMapper.insert(snapshot);
                return;
            } catch (DuplicateKeyException e) {
                // 并发下另一事务已插入，重新读取后走更新分支
                list = snapshotMapper.selectList(
                        new LambdaQueryWrapper<BurnDownSnapshot>()
                                .eq(BurnDownSnapshot::getProjectId, projectId)
                                .eq(BurnDownSnapshot::getTeamId, teamId)
                                .eq(BurnDownSnapshot::getSnapshotDate, today));
                if (list.isEmpty()) return;
            }
        }

        // 更新首条；若存在瞬态重复行则清理其余（自愈）
        BurnDownSnapshot target = list.get(0);
        target.setTotalCount(totalCount.intValue());
        target.setRemainingCount(remainingCount.intValue());
        snapshotMapper.updateById(target);
        for (int i = 1; i < list.size(); i++) {
            snapshotMapper.deleteById(list.get(i).getId());
        }
    }

    /**
     * 获取项目的燃尽图时间序列（按快照日期升序）。
     * 仅返回属于当前团队的快照，每条含快照日期 / 剩余任务数 / 总任务数。
     */
    public List<Map<String, Object>> getBurndownData(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        List<BurnDownSnapshot> snapshots = snapshotMapper.selectList(
                new LambdaQueryWrapper<BurnDownSnapshot>()
                        .eq(BurnDownSnapshot::getProjectId, projectId)
                        .eq(BurnDownSnapshot::getTeamId, teamId)
                        .orderByAsc(BurnDownSnapshot::getSnapshotDate));

        List<Map<String, Object>> result = new ArrayList<>();
        for (BurnDownSnapshot s : snapshots) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("snapshotDate", s.getSnapshotDate().toString());
            m.put("remainingCount", s.getRemainingCount());
            m.put("totalCount", s.getTotalCount());
            result.add(m);
        }
        return result;
    }
}
