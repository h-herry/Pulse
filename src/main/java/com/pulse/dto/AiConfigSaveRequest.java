package com.pulse.dto;

import lombok.Data;

/**
 * 保存用户 LLM 配置请求体。
 */
@Data
public class AiConfigSaveRequest {
    private String baseUrl;
    private String apiKey;
    private String model;
    private boolean enabled = true;
}
