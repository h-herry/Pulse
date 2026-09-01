package com.pulse.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 统一填充带 {@code @TableField(fill = ...)} 注解的时间字段。
 * 实体未显式赋值这些字段时，由 MyBatis-Plus 在 INSERT / UPDATE 时自动写入当前时间，
 * 避免实体 null 值被显式写入 SQL 从而触发数据库 NOT NULL 约束。
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        strictInsertFill(metaObject, "joinedAt", LocalDateTime::now, LocalDateTime.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
    }
}
