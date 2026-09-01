package com.pulse.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.TenantContext;
import com.pulse.entity.Project;
import com.pulse.mapper.ProjectMapper;
import com.pulse.service.BurndownService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 燃尽快照定时任务。
 *
 * <p>每日凌晨 2 点对所有未（逻辑）删除的项目补采一份燃尽快照，作为兜底：
 * 实时快照由任务流转时触发，本任务保证「当天无任何任务变更」也留存一个数据点。
 * 每个项目处理前通过 {@link TenantContext} 设置团队上下文，
 * 使快照计算在正确的多租户数据范围内执行。
 */
@Component
@EnableScheduling
public class BurndownScheduler {

    private final ProjectMapper projectMapper;
    private final BurndownService burndownService;

    public BurndownScheduler(ProjectMapper projectMapper, BurndownService burndownService) {
        this.projectMapper = projectMapper;
        this.burndownService = burndownService;
    }

    /**
     * 每日凌晨 2 点对所有未归档项目补一份燃尽快照（兜底）。
     * 真正的实时快照由 TaskService 在任务创建/流转/删除时触发，
     * 这里只负责保证即使当天无任何任务变更也有一个数据点。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailySnapshot() {
        var projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().ne(Project::getStatus, "deleted"));

        for (Project p : projects) {
            try {
                TenantContext.setTenantId(p.getTeamId());
                burndownService.takeSnapshot(p.getId());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
