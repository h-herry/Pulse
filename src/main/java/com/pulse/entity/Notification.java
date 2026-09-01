package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 通知实体（对应表 {@code notifications}）。
 *
 * <p>面向单个用户（{@code userId}）的站内通知，可归属团队（{@code teamId}）。
 * {@code type} 区分通知类别，{@code isRead} 标记是否已读，{@code sourceKey} 用于去重/聚合同类通知。
 */
@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 接收通知的用户（外键 users.id） */
    private Long userId;
    /** 归属团队（外键 teams.id，多租户隔离边界；系统级通知可为 null） */
    private Long teamId;
    /** 通知类型（如任务指派、评论提及、状态变更等） */
    private String type;
    /** 通知标题 */
    private String title;
    /** 通知正文 */
    private String body;
    /** 跳转链接（前端点击后前往的路由/资源地址） */
    private String link;
    /** 来源去重键：相同来源的重复事件可据此合并，避免通知刷屏 */
    private String sourceKey;
    /** 是否已读：0=未读，1=已读 */
    private Integer isRead;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
