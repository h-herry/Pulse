package com.pulse.dto;

import lombok.Data;

/**
 * 调用 LLM 时使用的临时凭证（明文仅在内存中短暂存在，不落库、不序列化到响应）。
 */
@Data
public class LlmCredentials {
    private String baseUrl;
    private String apiKey;
    private String model;
}
