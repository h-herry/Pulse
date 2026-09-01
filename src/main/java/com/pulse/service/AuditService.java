package com.pulse.service;

import com.pulse.common.TenantContext;
import com.pulse.entity.AuditLog;
import com.pulse.entity.User;
import com.pulse.mapper.AuditLogMapper;
import com.pulse.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作审计服务（v7 增量）。
 *
 * 在敏感操作（成员管理、团队设置、项目增删改、管理员跨团队操作）执行后调用，
 * 将「谁、在什么团队、对什么、做了什么」持久化到 audit_logs 表，供管理员审计视图查询。
 *
 * 设计要点：
 * - 操作者信息从 TenantContext 获取（与鉴权一致），姓名在写入时反范式化存储；
 * - 写入失败不影响主业务流程（catch 仅记日志），保证审计不成为业务瓶颈或故障点；
 * - 不记录敏感明文（如重置后的临时密码）。
 */
@Slf4j
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    public AuditService(AuditLogMapper auditLogMapper, UserMapper userMapper) {
        this.auditLogMapper = auditLogMapper;
        this.userMapper = userMapper;
    }

    /**
     * 记录一条审计（团队 ID 取当前租户上下文）。
     */
    public void record(String action, String targetType, Long targetId, String detail) {
        record(action, TenantContext.getTenantId(), targetType, targetId, detail);
    }

    /**
     * 记录一条审计（显式指定团队 ID，用于管理员跨团队操作的归属团队）。
     */
    public void record(String action, Long teamId, String targetType, Long targetId, String detail) {
        try {
            AuditLog log = new AuditLog();
            Long actorId = TenantContext.getUserId();
            log.setActorUserId(actorId);
            if (actorId != null) {
                User u = userMapper.selectById(actorId);
                log.setActorName(u != null ? u.getDisplayName() : null);
            }
            log.setTeamId(teamId);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计写入失败绝不阻断主业务
            log.warn("[AUDIT] 审计日志写入失败 action={} target={}:{} error={}",
                    action, targetType, targetId, e.getMessage());
        }
    }
}
