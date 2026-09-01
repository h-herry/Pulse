package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新用户资料请求（v6 修复 P2-2）。
 * 早期该接口用裸 Map 接收且无任何校验，可写入空串/超长字符串造成脏数据或列静默截断。
 */
public class AdminUpdateProfileRequest {
    @NotBlank(message = "显示名称不能为空")
    @Size(max = 100, message = "显示名称最多 100 字符")
    private String displayName;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
