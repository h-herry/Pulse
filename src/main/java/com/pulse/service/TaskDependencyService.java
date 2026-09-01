package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.entity.Task;
import com.pulse.entity.TaskDependency;
import com.pulse.mapper.TaskDependencyMapper;
import com.pulse.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务依赖服务（R-021）
 *
 * 添加依赖时做 DAG 校验，递归检查 dependsOnId 的依赖链是否已包含 taskId，防止循环依赖。
 */
@Service
public class TaskDependencyService {

    private final TaskDependencyMapper dependencyMapper;
    private final TaskMapper taskMapper;

    public TaskDependencyService(TaskDependencyMapper dependencyMapper, TaskMapper taskMapper) {
        this.dependencyMapper = dependencyMapper;
        this.taskMapper = taskMapper;
    }

    private Task verifyTask(Long taskId, Long teamId) {
        Task t = taskMapper.selectById(taskId);
        if (t == null || !t.getTeamId().equals(teamId)) {
            throw new BusinessException("任务不存在");
        }
        return t;
    }

    public List<Map<String, Object>> list(Long taskId, Long teamId) {
        verifyTask(taskId, teamId);
        List<TaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId)
                .eq(TaskDependency::getTeamId, teamId)
                .orderByAsc(TaskDependency::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskDependency d : deps) {
            Task dep = taskMapper.selectById(d.getDependsOnId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("taskId", d.getTaskId());
            m.put("dependsOnId", d.getDependsOnId());
            m.put("dependsOnTitle", dep != null ? dep.getTitle() : null);
            m.put("dependsOnCode", dep != null ? dep.getTaskNumber() : null);
            m.put("createdAt", d.getCreatedAt());
            result.add(m);
        }
        return result;
    }

    @Transactional
    public TaskDependency create(Long taskId, Long dependsOnId, Long teamId) {
        Task task = verifyTask(taskId, teamId);
        Task depends = verifyTask(dependsOnId, teamId);
        if (taskId.equals(dependsOnId)) {
            throw new BusinessException("任务不能依赖自身");
        }
        // DAG 校验：若 dependsOnId 的依赖链中已包含 taskId，则形成环
        if (wouldCreateCycle(taskId, dependsOnId, teamId)) {
            throw new BusinessException("依赖关系会形成循环，已阻止");
        }
        // 去重
        boolean exists = dependencyMapper.exists(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, taskId)
                .eq(TaskDependency::getDependsOnId, dependsOnId));
        if (exists) {
            throw new BusinessException("依赖关系已存在");
        }
        TaskDependency d = new TaskDependency();
        d.setTaskId(taskId);
        d.setDependsOnId(dependsOnId);
        d.setTeamId(teamId);
        dependencyMapper.insert(d);
        return d;
    }

    /**
     * 判断 taskId 依赖 dependsOnId 是否会成环：
     * 从 dependsOnId 出发，沿其所有前置依赖遍历，若可达 taskId 则成环。
     */
    private boolean wouldCreateCycle(Long taskId, Long dependsOnId, Long teamId) {
        return traverseDependencies(dependsOnId, taskId, teamId, new java.util.HashSet<>());
    }

    private boolean traverseDependencies(Long currentDepId, Long targetTaskId, Long teamId, Set<Long> visited) {
        if (currentDepId.equals(targetTaskId)) return true;
        if (!visited.add(currentDepId)) return false;
        List<TaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getTaskId, currentDepId)
                .eq(TaskDependency::getTeamId, teamId));
        for (TaskDependency d : deps) {
            if (traverseDependencies(d.getDependsOnId(), targetTaskId, teamId, visited)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void delete(Long depId, Long teamId) {
        TaskDependency d = dependencyMapper.selectById(depId);
        if (d == null || !d.getTeamId().equals(teamId)) {
            throw new BusinessException("依赖关系不存在");
        }
        dependencyMapper.deleteById(depId);
    }
}
