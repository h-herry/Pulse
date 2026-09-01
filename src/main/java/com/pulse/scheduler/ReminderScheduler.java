package com.pulse.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.TenantContext;
import com.pulse.entity.Task;
import com.pulse.entity.Team;
import com.pulse.mapper.TaskMapper;
import com.pulse.mapper.TeamMapper;
import com.pulse.mapper.UserMapper;
import com.pulse.service.NotificationService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能提醒调度器：每日扫描逾期 / 临期任务，按指派人汇总为「每日摘要」推送。
 * 同一用户同一天同一类型只推送一次（依赖 notifications.source_key 去重）。
 */
@Component
@EnableScheduling
public class ReminderScheduler {

    private final TaskMapper taskMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public ReminderScheduler(TaskMapper taskMapper, TeamMapper teamMapper,
                             UserMapper userMapper, NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.teamMapper = teamMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /** 每日 08:00 触发 */
    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyReminder() {
        runNow();
    }

    /** 手动触发（供运维/调试调用，也可被控制器暴露） */
    public int runNow() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        List<Team> teams = teamMapper.selectList(null);
        int pushed = 0;

        for (Team team : teams) {
            try {
                TenantContext.setTenantId(team.getId());

                List<Task> overdue = taskMapper.selectList(
                        new LambdaQueryWrapper<Task>()
                                .eq(Task::getTeamId, team.getId())
                                .ne(Task::getStatus, "DONE")
                                .lt(Task::getDueDate, today));
                List<Task> dueSoon = taskMapper.selectList(
                        new LambdaQueryWrapper<Task>()
                                .eq(Task::getTeamId, team.getId())
                                .ne(Task::getStatus, "DONE")
                                .ge(Task::getDueDate, today)
                                .le(Task::getDueDate, tomorrow));

                pushed += pushDigest(team.getId(), overdue, "逾期任务提醒",
                        "reminder:overdue:" + today, "逾期");
                pushed += pushDigest(team.getId(), dueSoon, "临期任务提醒",
                        "reminder:due:" + today, "即将到期");
            } finally {
                TenantContext.clear();
            }
        }
        return pushed;
    }

    private int pushDigest(Long teamId, List<Task> tasks, String title, String sourceKey, String label) {
        if (tasks.isEmpty()) return 0;
        Map<Long, List<Task>> byAssignee = tasks.stream()
                .filter(t -> t.getAssigneeId() != null)
                .collect(Collectors.groupingBy(Task::getAssigneeId));

        int pushed = 0;
        for (Map.Entry<Long, List<Task>> e : byAssignee.entrySet()) {
            Long assigneeId = e.getKey();
            List<Task> list = e.getValue();
            String names = list.stream().limit(5).map(Task::getTitle).collect(Collectors.joining("、"));
            String more = list.size() > 5 ? " 等" + list.size() + " 项" : "";
            String body = "你有 " + list.size() + " 个" + label + "任务：" + names + more;
            Task first = list.get(0);
            String link = "/team/" + teamId + "/project/" + first.getProjectId() + "/board?task=" + first.getId();
            notificationService.notifyWithKey(assigneeId, teamId, "REMINDER", title, body, link, sourceKey);
            pushed++;
        }
        return pushed;
    }
}
