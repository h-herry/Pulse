package com.pulse.dto;

import lombok.Data;

/**
 * 返回给前端的脱敏配置视图：绝不包含明文或密文 Key，
 * 仅告知是否已配置、基础地址、模型与启用状态。
 */
@Data
public class UserLlmConfigView {
    private String baseUrl;
    private String model;
    private Boolean enabled;
    private boolean configured;
    /** 占位提示，不泄漏任何明文/密文字节 */
    private String keyMasked;
}
