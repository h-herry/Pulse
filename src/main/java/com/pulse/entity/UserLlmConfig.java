package com.pulse.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户级 LLM 配置（per-user 自带 Key 模式）。
 * 与 users 表分离：避免被 JwtAuthFilter 每请求加载的 User 实体携带密文；
 * 仅在 AI 配置读写时按需加载。
 */
@Data
@TableName("user_llm_config")
public class UserLlmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("base_url")
    private String baseUrl;

    @TableField("model")
    private String model;

    /** AES-GCM 加密后的 API Key，绝不存明文 */
    @TableField("api_key_enc")
    private String apiKeyEnc;

    @TableField("enabled")
    private Boolean enabled = true;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
