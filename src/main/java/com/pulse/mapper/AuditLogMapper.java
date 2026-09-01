package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.AuditLog} 的 MyBatis-Plus Mapper，对应表 {@code audit_logs}。
 * 继承 {@code BaseMapper<AuditLog>}，提供操作审计日志的增删改查与分页能力。
 */
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
