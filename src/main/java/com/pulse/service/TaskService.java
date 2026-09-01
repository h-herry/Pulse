package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.dto.MemberWorkload;
import com.pulse.dto.TaskMoveRequest;
import com.pulse.dto.TaskPatchRequest;
import com.pulse.dto.TaskRequest;
import com.pulse.entity.*;
import com.pulse.mapper.*;
import com.pulse.service.BurndownService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;

/**
 * 任务核心服务（R-010~R-019）。
 * 负责任务 CRUD、看板移动（含 WIP 限制）、子任务、标签关联、活动日志、搜索、
 * 跨团队「我的任务」、成员负载计算与批量更新。
 * 权限与安全：所有操作基于 TenantContext 做租户隔离；指派变更校验目标用户属当前团队；
 * 跨团队「我的任务」仅以 assignee=当前用户 为安全边界；任务流转自动触发燃尽快照与活动日志。
 */
@Service
public class TaskService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    private final TaskMapper taskMapper;
    private final TaskActivityLogMapper logMapper;
    private final BoardColumnMapper columnMapper;
    private final ProjectMapper projectMapper;
    private final TaskTagMapper taskTagMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final NotificationService notificationService;
    private final BurndownService burndownService;
    private final TaskSequenceMapper taskSequenceMapper;
    private final AuthorizationService authorizationService;

    public TaskService(TaskMapper taskMapper, TaskActivityLogMapper logMapper,
                       BoardColumnMapper columnMapper, ProjectMapper projectMapper,
                       TaskTagMapper taskTagMapper, TagMapper tagMapper, UserMapper userMapper,
                       TeamMemberMapper teamMemberMapper, TeamMapper teamMapper,
                       NotificationService notificationService, BurndownService burndownService,
                       TaskSequenceMapper taskSequenceMapper, AuthorizationService authorizationService) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.columnMapper = columnMapper;
        this.projectMapper = projectMapper;
        this.taskTagMapper = taskTagMapper;
        this.tagMapper = tagMapper;
        this.userMapper = userMapper;
        this.teamMemberMapper = teamMemberMapper;
        this.teamMapper = teamMapper;
        this.notificationService = notificationService;
        this.burndownService = burndownService;
        this.taskSequenceMapper = taskSequenceMapper;
        this.authorizationService = authorizationService;
    }

    /**
     * 创建任务（当前团队内）。
     * 校验项目归属与指派人团队资格；基于每项目序列行 + 行锁生成唯一任务号；
     * 状态变更自动映射到看板列；创建后记录燃尽快照、活动日志并通知指派人。
     */
    @Transactional
    public Task createTask(Long projectId, TaskRequest req) {
        Long teamId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        Project project = projectMapper.selectById(projectId);
        if (project == null || !project.getTeamId().equals(teamId)) {
            throw new BusinessException("项目不存在");
        }

        // Validate assignee belongs to the team
        if (req.getAssigneeId() != null) {
            boolean isMember = teamMemberMapper.exists(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, teamId)
                            .eq(TeamMember::getUserId, req.getAssigneeId()));
            if (!isMember) {
                throw new BusinessException("该用户不属于当前团队");
            }
        }

        // 任务号生成：基于每项目序列行 + 行锁（FOR UPDATE）。
        // 序列行在「项目创建时」与「应用启动引导」时已就绪，热路径只做加锁读取，
        // 不会触发并发 INSERT，从而避免间隙锁/记录锁死锁。同项目并发建任务零冲突、零失败。
        TaskSequence seq = taskSequenceMapper.selectForUpdate(projectId);
        if (seq == null) {
            // 兜底：极端情况下序列行缺失时再初始化（单事务内完成，正常不会走到）
            ensureTaskSequence(projectId);
            seq = taskSequenceMapper.selectForUpdate(projectId);
        }
        int nextNumber = seq.getNextVal();
        taskSequenceMapper.increment(projectId);

        Task task = new Task();
        task.setProjectId(projectId);
        task.setTeamId(teamId);
        task.setReporterId(userId);
        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription() != null ? HtmlUtils.htmlEscape(req.getDescription()) : null);
        task.setStatus(req.getStatus() != null ? req.getStatus() : "TODO");
        task.setPriority(req.getPriority() != null ? req.getPriority() : "P2");
        task.setAssigneeId(req.getAssigneeId());
        task.setIterationId(req.getIterationId());
        task.setBoardColumnId(req.getBoardColumnId());
        task.setParentId(req.getParentId());
        task.setStoryPoints(req.getStoryPoints());
        task.setStartDate(req.getStartDate());
        task.setDueDate(req.getDueDate());

        if (task.getBoardColumnId() == null) {
            BoardColumn col = columnMapper.selectList(
                    new LambdaQueryWrapper<BoardColumn>()
                            .eq(BoardColumn::getProjectId, projectId)
                            .eq(BoardColumn::getMappedStatus, task.getStatus())
                            .orderByAsc(BoardColumn::getSortOrder))
                    .stream().findFirst().orElse(null);
            if (col != null) task.setBoardColumnId(col.getId());
        }

        task.setTaskNumber(nextNumber);

        Long count = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getBoardColumnId, task.getBoardColumnId()));
        task.setSortOrder(count.intValue());

        taskMapper.insert(task);

        // 任务新增会改变总量，自动记录一次燃尽快照（实时反映到报表）
        burndownService.takeSnapshot(projectId);

        logActivity(task.getId(), userId, teamId, "CREATED", null, "{\"title\":\"" + task.getTitle() + "\"}", null);
        if (task.getAssigneeId() != null) {
            notificationService.notify(task.getAssigneeId(), teamId, "TASK_ASSIGNED",
                    "你被指派了任务", task.getTitle(), "/team/" + teamId + "/project/" + projectId + "/task/" + task.getId());
        }
        return task;
    }

    /**
     * 幂等初始化项目的任务号序列行：取当前最大任务号 + 1 作为起点。
     * 并发下首个事务写入成功，其余因主键冲突被忽略（DuplicateKeyException）。
     */
    private void ensureTaskSequence(Long projectId) {
        Integer max = taskMapper.selectMaxTaskNumber(projectId);
        Integer next = (max == null ? 0 : max) + 1;
        try {
            taskSequenceMapper.insertSeq(projectId, next);
        } catch (DuplicateKeyException e) {
            // 其他并发事务已初始化，忽略即可
        }
    }

    /**
     * 分页查询项目的顶层任务（排除子任务），支持按列 / 指派人 / 迭代过滤，按 sortOrder 升序。
     * 基于当前团队隔离，并附带项目标识（projectKey）。
     */
    public IPage<Map<String, Object>> listTasks(Long projectId, int page, int size,
                                                  Long columnId, Long assigneeId, Long iterationId) {
        Long teamId = TenantContext.getTenantId();
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getTeamId, teamId)
                .isNull(Task::getParentId);
        if (columnId != null) wrapper.eq(Task::getBoardColumnId, columnId);
        if (assigneeId != null) wrapper.eq(Task::getAssigneeId, assigneeId);
        if (iterationId != null) wrapper.eq(Task::getIterationId, iterationId);
        wrapper.orderByAsc(Task::getSortOrder);

        Page<Task> pg = taskMapper.selectPage(new Page<>(page, size), wrapper);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Task t : pg.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("assigneeId", t.getAssigneeId());
            m.put("boardColumnId", t.getBoardColumnId());
            m.put("iterationId", t.getIterationId());
            m.put("storyPoints", t.getStoryPoints());
            m.put("dueDate", t.getDueDate());
            m.put("taskNumber", t.getTaskNumber());
            m.put("sortOrder", t.getSortOrder());
            m.put("projectKey",
                    projectMapper.selectById(projectId).getProjectKey());
            records.add(m);
        }

        IPage<Map<String, Object>> result = new Page<>(page, size, pg.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 按 ID 获取任务并做归属校验：属于当前团队、或当前用户为该任务负责人（支持跨团队看自己任务）才放行。
     */
    public Task getTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // 租户隔离兜底：任务所属团队需与当前上下文一致；
        // 但若当前用户就是该任务的负责人（必然属于任务所在团队），同样放行，
        // 以支持跨团队「我的任务」场景下查看/编辑自己负责的任务。
        Long currentUserId = TenantContext.getUserId();
        boolean teamOk = task.getTeamId().equals(TenantContext.getTenantId());
        boolean assigneeOk = currentUserId != null && task.getAssigneeId() != null
                && task.getAssigneeId().equals(currentUserId);
        if (!teamOk && !assigneeOk) {
            throw new BusinessException("无权访问");
        }
        return task;
    }

    /**
     * 获取任务详情（含项目标识、负责人与报告人姓名），先做 getTask 归属校验。
     */
    public Map<String, Object> getTaskDetail(Long id) {
        Task task = getTask(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", task.getId());
        m.put("projectId", task.getProjectId());
        m.put("teamId", task.getTeamId());
        m.put("parentId", task.getParentId());
        m.put("assigneeId", task.getAssigneeId());
        m.put("reporterId", task.getReporterId());
        m.put("iterationId", task.getIterationId());
        m.put("boardColumnId", task.getBoardColumnId());
        m.put("title", task.getTitle());
        m.put("description", task.getDescription());
        m.put("status", task.getStatus());
        m.put("priority", task.getPriority());
        m.put("storyPoints", task.getStoryPoints());
        m.put("startDate", task.getStartDate());
        m.put("dueDate", task.getDueDate());
        m.put("sortOrder", task.getSortOrder());
        m.put("taskNumber", task.getTaskNumber());
        m.put("createdAt", task.getCreatedAt());
        m.put("updatedAt", task.getUpdatedAt());

        Project project = projectMapper.selectById(task.getProjectId());
        m.put("projectKey", project != null ? project.getProjectKey() : null);

        User assignee = task.getAssigneeId() != null ? userMapper.selectById(task.getAssigneeId()) : null;
        m.put("assigneeName", assignee != null ? assignee.getDisplayName() : null);

        User reporter = task.getReporterId() != null ? userMapper.selectById(task.getReporterId()) : null;
        m.put("reporterName", reporter != null ? reporter.getDisplayName() : null);

        return m;
    }

    /**
     * 局部更新任务（标题 / 描述 / 状态 / 优先级 / 指派人 / 迭代 / 故事点 / 起止日期等）。
     * 指派人变更校验团队资格；状态变更自动映射看板列并触发燃尽快照与指派通知；记录活动日志。
     */
    public Task updateTask(Long id, TaskPatchRequest req) {
        Task task = getTask(id);
        Long userId = TenantContext.getUserId();

        Map<String, Object> oldVals = new LinkedHashMap<>();
        oldVals.put("title", task.getTitle());
        oldVals.put("status", task.getStatus());
        oldVals.put("priority", task.getPriority());
        oldVals.put("assigneeId", task.getAssigneeId());

        if (req.getTitle() != null) task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(HtmlUtils.htmlEscape(req.getDescription()));
        if (req.getStatus() != null) {
            String oldStatus = task.getStatus();
            task.setStatus(req.getStatus());
            if (!oldStatus.equals(req.getStatus())) {
                BoardColumn col = columnMapper.selectList(
                        new LambdaQueryWrapper<BoardColumn>()
                                .eq(BoardColumn::getProjectId, task.getProjectId())
                                .eq(BoardColumn::getMappedStatus, req.getStatus())
                                .orderByAsc(BoardColumn::getSortOrder))
                        .stream().findFirst().orElse(null);
                if (col != null) task.setBoardColumnId(col.getId());
            }
        }
        if (req.getPriority() != null) task.setPriority(req.getPriority());
        if (req.getAssigneeId() != null) {
            // Validate assignee belongs to the team
            boolean isMember = teamMemberMapper.exists(
                    new LambdaQueryWrapper<TeamMember>()
                            .eq(TeamMember::getTeamId, task.getTeamId())
                            .eq(TeamMember::getUserId, req.getAssigneeId()));
            if (!isMember) {
                throw new BusinessException("该用户不属于当前团队");
            }
            task.setAssigneeId(req.getAssigneeId());
        }
        if (req.getIterationId() != null) task.setIterationId(req.getIterationId());
        if (req.getStoryPoints() != null) task.setStoryPoints(req.getStoryPoints());
        if (req.getStartDate() != null) task.setStartDate(req.getStartDate());
        if (req.getDueDate() != null) task.setDueDate(req.getDueDate());

        taskMapper.updateById(task);

        // 状态流转影响燃尽图，自动记录快照
        if (req.getStatus() != null && !req.getStatus().equals(oldVals.get("status"))) {
            burndownService.takeSnapshot(task.getProjectId());
        }

        if (req.getAssigneeId() != null && !req.getAssigneeId().equals(oldVals.get("assigneeId"))) {
            notificationService.notify(req.getAssigneeId(), task.getTeamId(), "TASK_ASSIGNED",
                    "你被指派了任务", task.getTitle(),
                    "/team/" + task.getTeamId() + "/project/" + task.getProjectId() + "/task/" + task.getId());
        }

        Map<String, Object> newVals = new LinkedHashMap<>();
        newVals.put("title", task.getTitle());
        newVals.put("status", task.getStatus());
        newVals.put("priority", task.getPriority());
        newVals.put("assigneeId", task.getAssigneeId());

        logActivity(task.getId(), userId, task.getTeamId(), "UPDATED",
                toJson(oldVals), toJson(newVals), null);
        return task;
    }

    /**
     * 看板移动任务到目标列：校验目标列属同一项目，WIP 超限则拒绝；
     * 重排 sortOrder 后更新状态为列映射状态，触发燃尽快照与活动日志。
     */
    @Transactional
    public void moveTask(Long id, TaskMoveRequest req) {
        Task task = getTask(id);
        BoardColumn targetCol = columnMapper.selectById(req.getTargetColumnId());
        if (targetCol == null || !targetCol.getProjectId().equals(task.getProjectId())) {
            throw new BusinessException("目标列不存在");
        }

        if (targetCol.getWipLimit() != null && targetCol.getWipLimit() > 0
                && !targetCol.getId().equals(task.getBoardColumnId())) {
            Long count = taskMapper.selectCount(
                    new LambdaQueryWrapper<Task>()
                            .eq(Task::getBoardColumnId, targetCol.getId())
                            .ne(Task::getStatus, "DONE"));
            if (count >= targetCol.getWipLimit()) {
                throw new BusinessException("该列已达到在制品限制（WIP=" + targetCol.getWipLimit() + "）");
            }
        }

        taskMapper.decrementSortOrder(task.getBoardColumnId(), task.getSortOrder() + 1);

        task.setBoardColumnId(targetCol.getId());
        task.setStatus(targetCol.getMappedStatus());

        if (req.getAfterTaskId() != null) {
            Task after = taskMapper.selectById(req.getAfterTaskId());
            if (after != null) {
                task.setSortOrder(after.getSortOrder() + 1);
                taskMapper.incrementSortOrder(targetCol.getId(), task.getSortOrder());
            }
        } else {
            task.setSortOrder(0);
            taskMapper.incrementSortOrder(targetCol.getId(), 0);
        }

        taskMapper.updateById(task);
        // 列移动会改变任务状态（从而影响燃尽图剩余量），自动记录快照
        burndownService.takeSnapshot(task.getProjectId());
        logActivity(id, TenantContext.getUserId(), task.getTeamId(), "MOVED",
                null, "{\"column\":\"" + targetCol.getName() + "\"}", null);
    }

    /**
     * 删除任务并修正所在列后续任务的 sortOrder；触发燃尽快照。
     */
    public void deleteTask(Long id) {
        Task task = getTask(id);
        // 破坏性删除：仅团队 LEAD 或系统管理员
        authorizationService.requireTeamLead(task.getTeamId());
        Long projectId = task.getProjectId();
        taskMapper.deleteById(id);
        taskMapper.decrementSortOrder(task.getBoardColumnId(), task.getSortOrder() + 1);
        // 任务删除会改变总量，自动记录快照
        burndownService.takeSnapshot(projectId);
    }

    /**
     * 获取看板全部顶层任务（不含子任务），迭代维度可选（见重载方法）。
     */
    public List<Map<String, Object>> getBoardTasks(Long projectId) {
        return getBoardTasks(projectId, null);
    }

    /**
     * 获取看板全部顶层任务（可选按迭代过滤），批量关联标签与指派人信息以避免 N+1。
     * 基于当前团队隔离，附带项目标识。
     */
    public List<Map<String, Object>> getBoardTasks(Long projectId, Long iterationId) {
        Long teamId = TenantContext.getTenantId();
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .eq(Task::getTeamId, teamId)
                .isNull(Task::getParentId);
        if (iterationId != null) {
            wrapper.eq(Task::getIterationId, iterationId);
        }
        List<Task> list = taskMapper.selectList(wrapper.orderByAsc(Task::getSortOrder));

        Project project = projectMapper.selectById(projectId);
        String projectKey = project != null ? project.getProjectKey() : null;

        // 批量查询所有任务的标签关联，避免 N+1
        List<Long> taskIds = list.stream().map(Task::getId).toList();
        Map<Long, List<Tag>> taskTagsMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            List<TaskTag> allLinks = taskTagMapper.selectList(
                    new LambdaQueryWrapper<TaskTag>().in(TaskTag::getTaskId, taskIds));
            Set<Long> tagIds = new HashSet<>();
            for (TaskTag link : allLinks) {
                tagIds.add(link.getTagId());
            }
            Map<Long, Tag> tagMap = new HashMap<>();
            if (!tagIds.isEmpty()) {
                for (Tag tag : tagMapper.selectBatchIds(tagIds)) {
                    tagMap.put(tag.getId(), tag);
                }
            }
            for (TaskTag link : allLinks) {
                Tag tag = tagMap.get(link.getTagId());
                if (tag != null) {
                    taskTagsMap.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(tag);
                }
            }
        }

        // 批量查询所有 assignee 信息
        Set<Long> assigneeIds = new HashSet<>();
        for (Task t : list) {
            if (t.getAssigneeId() != null) assigneeIds.add(t.getAssigneeId());
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!assigneeIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(assigneeIds)) {
                userMap.put(u.getId(), u);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("assigneeId", t.getAssigneeId());
            m.put("boardColumnId", t.getBoardColumnId());
            m.put("iterationId", t.getIterationId());
            m.put("storyPoints", t.getStoryPoints());
            m.put("dueDate", t.getDueDate());
            m.put("taskNumber", t.getTaskNumber());
            m.put("sortOrder", t.getSortOrder());
            m.put("description", t.getDescription());
            m.put("projectKey", projectKey);

            User assignee = t.getAssigneeId() != null ? userMap.get(t.getAssigneeId()) : null;
            m.put("assigneeName", assignee != null ? assignee.getDisplayName() : null);

            // 关联标签
            List<Map<String, Object>> tagsList = new ArrayList<>();
            List<Tag> tags = taskTagsMap.get(t.getId());
            if (tags != null) {
                for (Tag tag : tags) {
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("id", tag.getId());
                    tm.put("name", tag.getName());
                    tm.put("color", tag.getColor());
                    tagsList.add(tm);
                }
            }
            m.put("tags", tagsList);
            result.add(m);
        }
        return result;
    }

    /**
     * 查询任务的活动日志（按时间倒序）；先做归属校验。
     */
    public List<TaskActivityLog> getTaskLogs(Long taskId) {
        getTask(taskId); // verify team ownership
        return logMapper.selectList(
                new LambdaQueryWrapper<TaskActivityLog>()
                        .eq(TaskActivityLog::getTaskId, taskId)
                        .orderByDesc(TaskActivityLog::getCreatedAt));
    }

    /**
     * 查询任务的子任务列表（按 sortOrder 升序）；先做父任务归属校验。
     */
    public List<Map<String, Object>> getSubtasks(Long parentId) {
        Task parent = getTask(parentId); // team ownership check
        List<Task> subs = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getParentId, parentId)
                        .eq(Task::getTeamId, parent.getTeamId())
                        .orderByAsc(Task::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task t : subs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("assigneeId", t.getAssigneeId());
            m.put("dueDate", t.getDueDate());
            m.put("taskNumber", t.getTaskNumber());
            m.put("done", "DONE".equals(t.getStatus()));
            result.add(m);
        }
        return result;
    }

    /**
     * 获取任务关联的标签列表；先做任务归属校验。
     */
    public List<Tag> getTaskTags(Long taskId) {
        getTask(taskId); // verify team ownership
        List<TaskTag> links = taskTagMapper.selectList(
                new LambdaQueryWrapper<TaskTag>().eq(TaskTag::getTaskId, taskId));
        if (links.isEmpty()) return Collections.emptyList();
        List<Long> tagIds = links.stream().map(TaskTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds);
    }

    /**
     * 全局搜索任务：在当前团队内匹配 title / description（LIKE 模糊查询）。
     *
     * @param keyword 关键词
     * @param teamId  团队 ID（须与 TenantContext 一致）
     * @param limit   返回条数上限（默认 20，最大 50）
     * @return 简化结果列表
     */
    public List<Map<String, Object>> searchTasks(String keyword, Long teamId, int limit) {
        if (!teamId.equals(TenantContext.getTenantId())) {
            throw new BusinessException("无权访问");
        }
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));

        // 查询团队下所有项目，建立 id → Project 映射
        List<Project> projects = projectMapper.selectList(
                new LambdaQueryWrapper<Project>().eq(Project::getTeamId, teamId));
        if (projects.isEmpty()) return Collections.emptyList();
        Map<Long, Project> projectMap = new HashMap<>();
        List<Long> projectIds = new ArrayList<>();
        for (Project p : projects) {
            projectMap.put(p.getId(), p);
            projectIds.add(p.getId());
        }

        // 搜索匹配 title 或 description 的任务
        String kw = "%" + keyword + "%";
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .in(Task::getProjectId, projectIds)
                        .eq(Task::getTeamId, teamId)
                        .isNull(Task::getParentId)
                        .and(w -> w.like(Task::getTitle, kw).or().like(Task::getDescription, kw))
                        .orderByDesc(Task::getCreatedAt)
                        .last("LIMIT " + safeLimit));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("taskNumber", t.getTaskNumber());
            Project p = projectMap.get(t.getProjectId());
            m.put("projectKey", p != null ? p.getProjectKey() : null);
            m.put("projectName", p != null ? p.getName() : null);
            m.put("projectId", t.getProjectId());
            m.put("status", t.getStatus());
            result.add(m);
        }
        return result;
    }

    /**
     * 跨团队「我的任务」：返回当前用户被分配的全部任务（不含子任务），
     * 不按 teamId 隔离，仅以 assignee = 当前用户 作为安全边界，
     * 因此天然不会泄露其他用户的数据。附带 团队 / 项目 / 负责人 信息以便前端分组。
     *
     * @return 任务列表（含 teamId / teamName / projectId / projectKey / projectName 等）
     */
    public List<Map<String, Object>> getMyTasks() {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return Collections.emptyList();
        }

        // 跨团队：只按负责人过滤，忽略 TenantContext 中的 teamId
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getAssigneeId, userId)
                        .isNull(Task::getParentId)
                        .orderByDesc(Task::getUpdatedAt));
        if (tasks.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载项目 / 团队，避免 N+1
        Set<Long> projectIds = tasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
        Map<Long, Project> projectMap = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));

        Set<Long> teamIds = tasks.stream().map(Task::getTeamId).collect(Collectors.toSet());
        Map<Long, Team> teamMap = teamMapper.selectBatchIds(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        User me = userMapper.selectById(userId);
        String myName = me != null ? me.getDisplayName() : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Task t : tasks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus());
            m.put("priority", t.getPriority());
            m.put("assigneeId", t.getAssigneeId());
            m.put("assigneeName", myName);
            m.put("reporterId", t.getReporterId());
            m.put("teamId", t.getTeamId());
            Team team = teamMap.get(t.getTeamId());
            m.put("teamName", team != null ? team.getName() : null);
            m.put("projectId", t.getProjectId());
            Project project = projectMap.get(t.getProjectId());
            m.put("projectKey", project != null ? project.getProjectKey() : null);
            m.put("projectName", project != null ? project.getName() : null);
            m.put("boardColumnId", t.getBoardColumnId());
            m.put("iterationId", t.getIterationId());
            m.put("storyPoints", t.getStoryPoints());
            m.put("dueDate", t.getDueDate());
            m.put("taskNumber", t.getTaskNumber());
            m.put("updatedAt", t.getUpdatedAt());
            result.add(m);
        }
        return result;
    }

    /**
     * 计算团队成员负载快照，供智能分派建议作为 AI 上下文。
     * 系统管理员（与团队角色解耦，隐藏于成员管理）不计入候选分派对象，与 listMembers 一致。
     * projectId 非空时，负载统计仅限该项目，实现「项目内负载均衡」。
     *
     * @return 成员负载列表（不含任何密钥明文）
     */
    public List<MemberWorkload> computeMemberWorkloads(Long teamId, Long projectId) {
        List<TeamMember> members = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>().eq(TeamMember::getTeamId, teamId));
        List<MemberWorkload> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (TeamMember tm : members) {
            User u = userMapper.selectById(tm.getUserId());
            // 系统管理员不参与分派（与团队管理隐藏 ADMIN 一致）
            if (u == null || Boolean.TRUE.equals(u.getIsSystemAdmin())) continue;

            LambdaQueryWrapper<Task> base = new LambdaQueryWrapper<Task>()
                    .eq(Task::getTeamId, teamId)
                    .eq(Task::getAssigneeId, tm.getUserId());
            if (projectId != null) base.eq(Task::getProjectId, projectId);

            long openTasks = taskMapper.selectCount(base.clone()
                    .ne(Task::getStatus, "DONE"));
            List<Task> openList = taskMapper.selectList(base.clone()
                    .ne(Task::getStatus, "DONE"));
            int openPoints = openList.stream()
                    .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
            long overdue = taskMapper.selectCount(base.clone()
                    .ne(Task::getStatus, "DONE")
                    .isNotNull(Task::getDueDate)
                    .lt(Task::getDueDate, today));
            long inProgress = taskMapper.selectCount(base.clone()
                    .in(Task::getStatus, "IN_PROGRESS", "REVIEW"));

            MemberWorkload mw = new MemberWorkload();
            mw.setUserId(tm.getUserId());
            mw.setDisplayName(u.getDisplayName());
            mw.setOpenTasks((int) openTasks);
            mw.setOpenStoryPoints(openPoints);
            mw.setOverdueTasks((int) overdue);
            mw.setInProgressTasks((int) inProgress);
            result.add(mw);
        }
        return result;
    }

    /**
     * 轻量归属校验：任务存在且属于指定团队则返回，否则返回 null。
     * 供智能分派等场景在不触发完整权限校验的前提下确认任务范围。
     */
    public Task getTaskIfInTeam(Long taskId, Long teamId) {
        Task t = taskMapper.selectById(taskId);
        if (t == null || !t.getTeamId().equals(teamId)) return null;
        return t;
    }

    @Transactional
    public void addTaskTag(Long taskId, Long tagId) {
        Task task = getTask(taskId);
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null || !tag.getTeamId().equals(task.getTeamId())) {
            throw new BusinessException("标签不存在");
        }
        TaskTag link = new TaskTag();
        link.setTaskId(taskId);
        link.setTagId(tagId);
        link.setTeamId(task.getTeamId());
        taskTagMapper.insert(link);
    }

    @Transactional
    public void removeTaskTag(Long taskId, Long tagId) {
        getTask(taskId); // verify team ownership
        taskTagMapper.delete(
                new LambdaQueryWrapper<TaskTag>()
                        .eq(TaskTag::getTaskId, taskId)
                        .eq(TaskTag::getTagId, tagId));
    }

    /**
     * 批量更新任务（R-019）。
     * 校验每个 task 属于当前团队（按 taskIds 批量查 + 校验 teamId），
     * 单次限 100 条，否则抛出 BusinessException。
     *
     * @param taskIds 任务 ID 列表
     * @param updates 待更新的字段集合（支持 status / assigneeId / priority / storyPoints）
     * @return 实际更新的任务数量
     */
    @Transactional
    public int batchUpdate(List<Long> taskIds, Map<String, Object> updates) {
        if (taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        if (taskIds.size() > 100) {
            throw new BusinessException("单次最多更新 100 条任务");
        }
        Long teamId = TenantContext.getTenantId();

        List<Task> tasks = taskMapper.selectBatchIds(taskIds);
        // 任一 ID 不存在 → 拒绝，避免静默部分更新
        if (tasks.size() != taskIds.size()) {
            Set<Long> foundIds = tasks.stream().map(Task::getId).collect(Collectors.toSet());
            for (Long id : taskIds) {
                if (!foundIds.contains(id)) {
                    throw new BusinessException("任务不存在: " + id);
                }
            }
        }
        // 越权校验：所有任务必须属于当前团队
        for (Task t : tasks) {
            if (!t.getTeamId().equals(teamId)) {
                throw new BusinessException("无权操作任务 " + t.getId());
            }
        }

        // 负责人变更需校验团队成员资格
        Object assigneeObj = updates.get("assigneeId");
        Long assigneeId = null;
        if (assigneeObj != null) {
            assigneeId = ((Number) assigneeObj).longValue();
            if (assigneeId != 0) {
                boolean isMember = teamMemberMapper.exists(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getTeamId, teamId)
                        .eq(TeamMember::getUserId, assigneeId));
                if (!isMember) {
                    throw new BusinessException("该用户不属于当前团队");
                }
            }
        }

        for (Task t : tasks) {
            if (updates.containsKey("status")) {
                t.setStatus(String.valueOf(updates.get("status")));
            }
            if (updates.containsKey("priority")) {
                t.setPriority(String.valueOf(updates.get("priority")));
            }
            if (assigneeObj != null) {
                t.setAssigneeId(assigneeId == 0 ? null : assigneeId);
            }
            if (updates.containsKey("storyPoints")) {
                Object sp = updates.get("storyPoints");
                t.setStoryPoints(sp == null ? null : ((Number) sp).intValue());
            }
            taskMapper.updateById(t);
        }
        return tasks.size();
    }

    private void logActivity(Long taskId, Long userId, Long teamId, String action,
                              String oldValues, String newValues, String comment) {
        TaskActivityLog log = new TaskActivityLog();
        log.setTaskId(taskId);
        log.setUserId(userId);
        log.setTeamId(teamId);
        log.setAction(action);
        log.setOldValues(oldValues);
        log.setNewValues(newValues);
        log.setComment(comment);
        logMapper.insert(log);
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
