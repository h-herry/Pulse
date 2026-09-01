package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.TenantContext;
import com.pulse.entity.Notification;
import com.pulse.mapper.NotificationMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知服务（R-007）。
 * 负责站内通知的创建（含 @提及、任务指派等）与实时推送（经 STOMP WebSocket），
 * 以及通知列表查询、未读数统计与已读标记。
 * 安全：notify 不通知自己；写操作按 userId + teamId 归属隔离，越权静默忽略。
 */
@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationMapper notificationMapper,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationMapper = notificationMapper;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 创建并推送一条通知给指定用户（不通知自己）。
     * 落库后经 STOMP 实时推送到该用户的通知通道；不抛出越权异常。
     */
    @Transactional
    public void notify(Long toUserId, Long teamId, String type, String title, String body, String link) {
        if (toUserId == null || toUserId.equals(TenantContext.getUserId())) return; // 不通知自己
        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setTeamId(teamId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setIsRead(0);
        send(n);
    }

    /**
     * 带去重键的通知（用于智能提醒每日摘要）。
     * 同一用户、同一团队、同一 sourceKey 已存在时跳过，保证「同日同类型提醒只推送一次」。
     */
    @Transactional
    public void notifyWithKey(Long toUserId, Long teamId, String type, String title, String body,
                              String link, String sourceKey) {
        if (toUserId == null) return;
        Long cnt = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, toUserId)
                        .eq(Notification::getTeamId, teamId)
                        .eq(Notification::getSourceKey, sourceKey));
        if (cnt != null && cnt > 0) return;
        Notification n = new Notification();
        n.setUserId(toUserId);
        n.setTeamId(teamId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setSourceKey(sourceKey);
        n.setIsRead(0);
        send(n);
    }

    private void send(Notification n) {
        notificationMapper.insert(n);

        // R-007: 通过 STOMP WebSocket 实时推送到用户的通知通道
        Map<String, Object> wsMsg = new LinkedHashMap<>();
        wsMsg.put("id", n.getId());
        wsMsg.put("type", n.getType());
        wsMsg.put("title", n.getTitle());
        wsMsg.put("body", n.getBody());
        wsMsg.put("link", n.getLink());
        wsMsg.put("createdAt", n.getCreatedAt());
        wsMsg.put("read", false);
        messagingTemplate.convertAndSend("/topic/notifications." + n.getUserId(), wsMsg);
    }

    /**
     * 查询当前用户的通知列表（按创建时间倒序，最多 limit 条），按当前团队隔离。
     */
    public List<Notification> listForUser(int limit) {
        Long userId = TenantContext.getUserId();
        Long teamId = TenantContext.getTenantId();
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getTeamId, teamId)
                        .orderByDesc(Notification::getCreatedAt)
                        .last("LIMIT " + limit));
    }

    /**
     * 统计当前用户在当前团队的未读通知数量。
     */
    public long unreadCount() {
        Long userId = TenantContext.getUserId();
        Long teamId = TenantContext.getTenantId();
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getTeamId, teamId)
                        .eq(Notification::getIsRead, 0));
    }

    /**
     * 标记单条通知为已读；若通知不属于当前用户或团队则静默忽略（防越权）。
     */
    @Transactional
    public void markRead(Long id) {
        Long userId = TenantContext.getUserId();
        Long teamId = TenantContext.getTenantId();
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId) || !n.getTeamId().equals(teamId)) {
            return; // 越权静默忽略
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    /**
     * 将当前用户在当前团队的全部未读通知批量标记为已读。
     */
    @Transactional
    public void markAllRead() {
        Long userId = TenantContext.getUserId();
        Long teamId = TenantContext.getTenantId();
        notificationMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getTeamId, teamId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1));
    }
}
