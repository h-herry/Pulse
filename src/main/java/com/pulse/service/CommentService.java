package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.dto.CommentRequest;
import com.pulse.entity.Task;
import com.pulse.entity.TaskComment;
import com.pulse.entity.User;
import com.pulse.mapper.TaskCommentMapper;
import com.pulse.mapper.TaskMapper;
import com.pulse.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.*;

/**
 * 任务评论服务（R-023）。
 * 负责任务评论的分页查询、创建与删除，并在 @提及 成员时经 NotificationService 推送通知。
 * 所有操作校验任务属于当前团队；删除评论仅限作者本人。
 */
@Service
public class CommentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TaskCommentMapper commentMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public CommentService(TaskCommentMapper commentMapper, TaskMapper taskMapper,
                          UserMapper userMapper, NotificationService notificationService) {
        this.commentMapper = commentMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /**
     * 分页查询任务评论，关联 users 表返回 displayName/avatarUrl。
     * 校验 task 属于当前团队。
     */
    public IPage<Map<String, Object>> listComments(Long taskId, int page, int size) {
        Long teamId = TenantContext.getTenantId();
        verifyTaskOwnership(taskId, teamId);

        Page<TaskComment> pg = commentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<TaskComment>()
                        .eq(TaskComment::getTaskId, taskId)
                        .eq(TaskComment::getTeamId, teamId)
                        .orderByDesc(TaskComment::getCreatedAt));

        // 批量查询评论作者信息
        Set<Long> userIds = new HashSet<>();
        for (TaskComment c : pg.getRecords()) {
            userIds.add(c.getUserId());
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userMapper.selectBatchIds(userIds)) {
                userMap.put(u.getId(), u);
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (TaskComment c : pg.getRecords()) {
            records.add(toMap(c, userMap));
        }

        IPage<Map<String, Object>> result = new Page<>(page, size, pg.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 创建评论，mentions 中的用户发通知。
     */
    @Transactional
    public Map<String, Object> createComment(Long taskId, CommentRequest req) {
        Long teamId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        Task task = verifyTaskOwnership(taskId, teamId);

        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }

        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setUserId(userId);
        comment.setTeamId(teamId);
        comment.setContent(HtmlUtils.htmlEscape(req.getContent()));
        comment.setMentions(toMentionsJson(req.getMentions()));
        commentMapper.insert(comment);

        // 为 @提及的用户发通知
        if (req.getMentions() != null) {
            for (Long mentionedUserId : req.getMentions()) {
                if (!mentionedUserId.equals(userId)) {
                    notificationService.notify(mentionedUserId, teamId, "TASK_MENTIONED",
                            "你被提及了",
                            "任务「" + task.getTitle() + "」中有新的 @提及",
                            "/team/" + teamId + "/project/" + task.getProjectId() + "/board?task=" + taskId);
                }
            }
        }

        // 返回带作者信息的评论
        Map<Long, User> userMap = new HashMap<>();
        User author = userMapper.selectById(userId);
        if (author != null) userMap.put(userId, author);
        return toMap(comment, userMap);
    }

    /**
     * 删除评论（仅作者本人可删除）。
     */
    @Transactional
    public void deleteComment(Long taskId, Long commentId) {
        Long teamId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();

        verifyTaskOwnership(taskId, teamId);

        TaskComment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getTaskId().equals(taskId) || !comment.getTeamId().equals(teamId)) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }
        commentMapper.deleteById(commentId);
    }

    // ===== Private helpers =====

    private Task verifyTaskOwnership(Long taskId, Long teamId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getTeamId().equals(teamId)) {
            throw new BusinessException("任务不存在或无权访问");
        }
        return task;
    }

    private Map<String, Object> toMap(TaskComment c, Map<Long, User> userMap) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("taskId", c.getTaskId());
        m.put("userId", c.getUserId());
        m.put("teamId", c.getTeamId());
        m.put("content", c.getContent());
        m.put("mentions", parseMentions(c.getMentions()));
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        User author = userMap.get(c.getUserId());
        m.put("displayName", author != null ? author.getDisplayName() : null);
        m.put("avatarUrl", author != null ? author.getAvatarUrl() : null);
        return m;
    }

    private String toMentionsJson(List<Long> mentions) {
        if (mentions == null || mentions.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(mentions);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseMentions(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readValue(json, List.class);
        } catch (Exception e) {
            return null;
        }
    }
}
