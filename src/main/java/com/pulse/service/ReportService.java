package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.entity.Iteration;
import com.pulse.entity.Project;
import com.pulse.entity.Task;
import com.pulse.entity.User;
import com.pulse.mapper.IterationMapper;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TaskMapper;
import com.pulse.mapper.UserMapper;
import com.pulse.service.BurndownService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 报表与统计服务（R-008）。
 * 聚合项目 / 迭代 / 团队 / 成员维度的任务统计，并生成燃尽图、速度图、周期时间、
 * 状态与优先级分布，以及任务 CSV 导出。
 * 所有查询基于 TenantContext 的 teamId 做租户隔离，越权返回或抛「无权限」。
 */
@Service
public class ReportService {

    private final TaskMapper taskMapper;
    private final IterationMapper iterationMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final BurndownService burndownService;

    public ReportService(TaskMapper taskMapper, IterationMapper iterationMapper,
                         ProjectMapper projectMapper, UserMapper userMapper,
                         BurndownService burndownService) {
        this.taskMapper = taskMapper;
        this.iterationMapper = iterationMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.burndownService = burndownService;
    }

    /**
     * 项目级任务统计：总数、已完成、待办、进行中、完成率（按当前团队隔离）。
     */
    public Map<String, Object> getProjectStats(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        Map<String, Object> stats = new LinkedHashMap<>();

        Long total = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId));
        Long done = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId)
                        .eq(Task::getStatus, "DONE"));
        Long todo = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId)
                        .eq(Task::getStatus, "TODO"));

        stats.put("totalTasks", total);
        stats.put("doneTasks", done);
        stats.put("todoTasks", todo);
        stats.put("inProgressTasks", total - done - todo);
        stats.put("progress", total > 0 ? Math.round(100.0 * done / total) : 0);

        return stats;
    }

    /**
     * 报表接口契约自洽（P3）：团队级报表路由在未传 projectId 时，
     * 自动取该团队的首个项目作为统计目标，消除「团队级路由却要项目级参数」的混淆。
     */
    public Long resolveProjectId(Long teamId, Long projectId) {
        if (projectId != null) return projectId;
        Project p = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, teamId)
                        .orderByAsc(Project::getId)
                        .last("LIMIT 1"));
        if (p == null) throw new BusinessException("团队暂无项目");
        return p.getId();
    }

    /**
     * 迭代级统计：任务总数 / 状态分布 / 故事点 / 逾期 / 速度趋势 / 燃尽区间，及上一迭代速度。
     * 供迭代复盘与 AI 复盘增强使用；团队隔离。
     */
    public Map<String, Object> getIterationStats(Long iterationId) {
        Long teamId = TenantContext.getTenantId();
        Iteration iter = iterationMapper.selectById(iterationId);
        if (iter == null) return Collections.emptyMap();

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getIterationId, iterationId)
                        .eq(Task::getTeamId, teamId));

        long total = tasks.size();
        long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long review = tasks.stream().filter(t -> "REVIEW".equals(t.getStatus())).count();
        long todo = tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
        long overdue = tasks.stream()
                .filter(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(LocalDate.now()))
                .count();

        int spTotal = tasks.stream().mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
        int spDone = tasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> overdueTasks = tasks.stream()
                .filter(t -> !"DONE".equals(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(today))
                .limit(20)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("title", t.getTitle());
                    m.put("dueDate", t.getDueDate().toString());
                    m.put("priority", t.getPriority());
                    User u = userMapper.selectById(t.getAssigneeId());
                    m.put("assigneeName", u != null ? u.getDisplayName() : "未指派");
                    return m;
                })
                .toList();

        // 上一迭代已完成故事点（速度趋势）
        List<Iteration> siblings = iterationMapper.selectList(
                new LambdaQueryWrapper<Iteration>()
                        .eq(Iteration::getProjectId, iter.getProjectId())
                        .eq(Iteration::getTeamId, teamId)
                        .orderByAsc(Iteration::getStartDate));
        int prevVelocity = 0;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(iter.getId()) && i > 0) {
                Iteration prev = siblings.get(i - 1);
                prevVelocity = taskMapper.selectList(
                        new LambdaQueryWrapper<Task>()
                                .eq(Task::getIterationId, prev.getId())
                                .eq(Task::getTeamId, teamId)
                                .eq(Task::getStatus, "DONE"))
                        .stream().mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
                break;
            }
        }

        // 迭代区间内燃尽图：取项目快照中落在 [startDate, endDate] 的点
        List<Map<String, Object>> burndown = new ArrayList<>();
        if (iter.getStartDate() != null && iter.getEndDate() != null) {
            List<Map<String, Object>> projectTrend = burndownService.getBurndownData(iter.getProjectId());
            for (Map<String, Object> p : projectTrend) {
                LocalDate d = LocalDate.parse(String.valueOf(p.get("snapshotDate")));
                if (!d.isBefore(iter.getStartDate()) && !d.isAfter(iter.getEndDate())) {
                    burndown.add(p);
                }
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("iterationId", iterationId);
        stats.put("iterationName", iter.getName());
        stats.put("status", iter.getStatus());
        stats.put("startDate", iter.getStartDate() != null ? iter.getStartDate().toString() : null);
        stats.put("endDate", iter.getEndDate() != null ? iter.getEndDate().toString() : null);
        stats.put("total", total);
        stats.put("done", done);
        stats.put("inProgress", inProgress);
        stats.put("review", review);
        stats.put("todo", todo);
        stats.put("progress", total > 0 ? Math.round(100.0 * done / total) : 0);
        stats.put("overdueCount", overdue);
        stats.put("overdueRate", total > 0 ? Math.round(100.0 * overdue / total) : 0);
        stats.put("overdueTasks", overdueTasks);
        stats.put("storyPointsTotal", spTotal);
        stats.put("storyPointsDone", spDone);
        stats.put("storyPointsProgress", spTotal > 0 ? Math.round(100.0 * spDone / spTotal) : 0);
        stats.put("velocity", spDone);
        stats.put("previousVelocity", prevVelocity);
        stats.put("burndown", burndown);

        Map<String, Object> statusCounts = new LinkedHashMap<>();
        statusCounts.put("TODO", todo);
        statusCounts.put("IN_PROGRESS", inProgress);
        statusCounts.put("REVIEW", review);
        statusCounts.put("DONE", done);
        stats.put("statusCounts", statusCounts);

        return stats;
    }

    /**
     * 按指派人的任务统计（总数 / 已完成 / 待办 / 进行中），仅统计已指派任务，按当前团队隔离。
     */
    public List<Map<String, Object>> getAssigneeStats(Long projectId) {
        Long teamId = TenantContext.getTenantId();
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, teamId)
                        .isNotNull(Task::getAssigneeId));

        // assigneeId -> [total, done, todo, inProgress]
        Map<Long, int[]> assigneeMap = new LinkedHashMap<>();
        for (Task t : tasks) {
            int[] c = assigneeMap.computeIfAbsent(t.getAssigneeId(), k -> new int[4]);
            c[0]++;
            if ("DONE".equals(t.getStatus())) c[1]++;
            else if ("TODO".equals(t.getStatus())) c[2]++;
            else c[3]++;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : assigneeMap.entrySet()) {
            Long assigneeId = e.getKey();
            int[] c = e.getValue();
            User u = userMapper.selectById(assigneeId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", assigneeId);
            m.put("displayName", u != null ? u.getDisplayName() : "未知用户");
            m.put("total", c[0]);
            m.put("done", c[1]);
            m.put("todo", c[2]);
            m.put("inProgress", c[3]);
            result.add(m);
        }
        return result;
    }

    // ======== 团队级报表方法 ========

    /**
     * 团队总览统计：该团队全部项目聚合的任务总数、已完成数与逾期数、完成率。
     */
    public Map<String, Object> getOverview(Long teamId) {
        Long tenantId = TenantContext.getTenantId();
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getTeamId, tenantId)
                        .ne(Project::getStatus, "deleted"));
        List<Long> projectIds = projects.stream().map(Project::getId).toList();

        long total, done, overdue;
        if (projectIds.isEmpty()) {
            total = done = overdue = 0;
        } else {
            total = taskMapper.selectCount(
                    new LambdaQueryWrapper<Task>()
                            .in(Task::getProjectId, projectIds)
                            .eq(Task::getTeamId, tenantId));
            done = taskMapper.selectCount(
                    new LambdaQueryWrapper<Task>()
                            .in(Task::getProjectId, projectIds)
                            .eq(Task::getTeamId, tenantId)
                            .eq(Task::getStatus, "DONE"));
            overdue = taskMapper.selectCount(
                    new LambdaQueryWrapper<Task>()
                            .in(Task::getProjectId, projectIds)
                            .eq(Task::getTeamId, tenantId)
                            .ne(Task::getStatus, "DONE")
                            .lt(Task::getDueDate, LocalDate.now()));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("done", done);
        stats.put("overdue", overdue);
        stats.put("progress", total > 0 ? Math.round(100.0 * done / total) : 0);
        return stats;
    }

    /**
     * 团队成员效率统计：以 assignee 聚合的任务总数与已完成数、完成率，按当前团队隔离。
     */
    public Map<String, Object> getMemberEfficiency(Long teamId) {
        Long tenantId = TenantContext.getTenantId();
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTeamId, tenantId)
                        .isNotNull(Task::getAssigneeId));

        Map<Long, int[]> memberMap = new LinkedHashMap<>();
        for (Task t : tasks) {
            memberMap.computeIfAbsent(t.getAssigneeId(), k -> new int[2]);
            int[] c = memberMap.get(t.getAssigneeId());
            c[0]++;
            if ("DONE".equals(t.getStatus())) c[1]++;
        }

        List<Map<String, Object>> members = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : memberMap.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assigneeId", e.getKey());
            m.put("total", e.getValue()[0]);
            m.put("done", e.getValue()[1]);
            m.put("progress", e.getValue()[0] > 0 ? Math.round(100.0 * e.getValue()[1] / e.getValue()[0]) : 0);
            members.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", members);
        return result;
    }

    /**
     * 迭代报表入口：校验迭代属于当前团队后返回迭代统计（同 getIterationStats）。
     */
    public Map<String, Object> getIterationReport(Long iterationId) {
        Long tenantId = TenantContext.getTenantId();
        Iteration iter = iterationMapper.selectById(iterationId);
        if (iter == null || !iter.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }
        return getIterationStats(iterationId);
    }

    /**
     * 项目燃尽趋势：校验项目归属后返回燃尽图数据序列。
     */
    public Map<String, Object> getTrend(Long projectId) {
        Long tenantId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }
        List<Map<String, Object>> data = burndownService.getBurndownData(projectId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("data", data);
        return result;
    }

    /**
     * R-008: 速度图 —— 各迭代已完成故事点。
     * 按迭代开始日期排序；未归属迭代的已完成任务归入「未规划」分组。
     */
    public List<Map<String, Object>> getVelocity(Long projectId) {
        Long tenantId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }

        List<Task> doneTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, tenantId)
                        .eq(Task::getStatus, "DONE"));

        // 按迭代分组累计已完成故事点（无迭代归为 -1）
        Map<Long, Integer> spByIter = new LinkedHashMap<>();
        for (Task t : doneTasks) {
            Long iterId = t.getIterationId() != null ? t.getIterationId() : -1L;
            spByIter.merge(iterId, t.getStoryPoints() != null ? t.getStoryPoints() : 0, Integer::sum);
        }

        List<Iteration> iterations = iterationMapper.selectList(
                new LambdaQueryWrapper<Iteration>()
                        .eq(Iteration::getProjectId, projectId)
                        .eq(Iteration::getTeamId, tenantId)
                        .orderByAsc(Iteration::getStartDate));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Iteration it : iterations) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", it.getName());
            m.put("completedStoryPoints", spByIter.getOrDefault(it.getId(), 0));
            result.add(m);
        }
        // 未规划（无迭代）的已完成故事点
        if (spByIter.containsKey(-1L) && spByIter.get(-1L) > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "未规划");
            m.put("completedStoryPoints", spByIter.get(-1L));
            result.add(m);
        }
        return result;
    }

    /**
     * R-008: 周期时间分布 —— 已完成任务从开始到完成的耗时分桶。
     * 完成时间近似取 updatedAt；无开始日期的任务不计入。
     */
    public Map<String, Object> getCycleTime(Long projectId) {
        Long tenantId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }

        List<Task> doneTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, tenantId)
                        .eq(Task::getStatus, "DONE"));

        String[] ranges = {"0-1天", "2-3天", "4-7天", "8-14天", "15天以上"};
        int[] counts = new int[ranges.length];
        for (Task t : doneTasks) {
            if (t.getStartDate() == null || t.getUpdatedAt() == null) continue;
            long days = ChronoUnit.DAYS.between(t.getStartDate(), t.getUpdatedAt().toLocalDate());
            if (days < 0) days = 0;
            int idx;
            if (days < 2) idx = 0;
            else if (days < 4) idx = 1;
            else if (days < 8) idx = 2;
            else if (days < 15) idx = 3;
            else idx = 4;
            counts[idx]++;
        }

        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("range", ranges[i]);
            m.put("count", counts[i]);
            buckets.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buckets", buckets);
        return result;
    }

    /**
     * R-008: 状态分布 —— 按任务状态计数（固定顺序 TODO/IN_PROGRESS/REVIEW/DONE）。
     */
    public Map<String, Object> getStatusDistribution(Long projectId) {
        Long tenantId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, tenantId));

        Map<String, Long> countMap = new LinkedHashMap<>();
        for (Task t : tasks) {
            countMap.merge(t.getStatus(), 1L, Long::sum);
        }

        String[] order = {"TODO", "IN_PROGRESS", "REVIEW", "DONE"};
        List<Map<String, Object>> items = new ArrayList<>();
        for (String status : order) {
            if (countMap.containsKey(status)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", status);
                m.put("count", countMap.get(status));
                items.add(m);
            }
        }
        // 兜底：其它未知状态
        for (Map.Entry<String, Long> e : countMap.entrySet()) {
            if (!Arrays.asList(order).contains(e.getKey())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("status", e.getKey());
                m.put("count", e.getValue());
                items.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        return result;
    }

    /**
     * R-008: 优先级分布 —— 按任务优先级计数（固定顺序 P0~P3）。
     */
    public Map<String, Object> getPriorityDistribution(Long projectId) {
        Long tenantId = TenantContext.getTenantId();
        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(tenantId)) {
            throw new BusinessException("无权限");
        }

        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getProjectId, projectId)
                        .eq(Task::getTeamId, tenantId));

        Map<String, Long> countMap = new LinkedHashMap<>();
        for (Task t : tasks) {
            countMap.merge(t.getPriority(), 1L, Long::sum);
        }

        String[] order = {"P0", "P1", "P2", "P3"};
        List<Map<String, Object>> items = new ArrayList<>();
        for (String priority : order) {
            if (countMap.containsKey(priority)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("priority", priority);
                m.put("count", countMap.get(priority));
                items.add(m);
            }
        }
        for (Map.Entry<String, Long> e : countMap.entrySet()) {
            if (!Arrays.asList(order).contains(e.getKey())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("priority", e.getKey());
                m.put("count", e.getValue());
                items.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        return result;
    }

    /**
     * 导出当前团队的全部任务为 CSV（UTF-8），按项目 / ID 升序；不暴露敏感字段。
     */
    public Resource exportTasks(Long teamId) {
        Long tenantId = TenantContext.getTenantId();
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTeamId, tenantId)
                        .orderByAsc(Task::getProjectId, Task::getId));

        StringBuilder sb = new StringBuilder();
        sb.append("任务编号,标题,状态,优先级,指派人ID,项目ID,迭代ID,截止日期\n");
        for (Task t : tasks) {
            sb.append(escapeCsv(t.getTaskNumber() != null ? String.valueOf(t.getTaskNumber()) : ""));
            sb.append(",").append(escapeCsv(t.getTitle() != null ? t.getTitle() : ""));
            sb.append(",").append(escapeCsv(t.getStatus() != null ? t.getStatus() : ""));
            sb.append(",").append(escapeCsv(t.getPriority() != null ? t.getPriority() : ""));
            sb.append(",").append(t.getAssigneeId() != null ? t.getAssigneeId() : "");
            sb.append(",").append(t.getProjectId() != null ? t.getProjectId() : "");
            sb.append(",").append(t.getIterationId() != null ? t.getIterationId() : "");
            sb.append(",").append(t.getDueDate() != null ? t.getDueDate().toString() : "");
            sb.append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return new ByteArrayResource(bytes);
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
