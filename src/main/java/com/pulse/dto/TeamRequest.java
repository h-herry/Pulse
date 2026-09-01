package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 团队创建/更新请求。
 *
 * <p>{@code name} 团队名，{@code description} 描述。团队为多租户一级隔离边界。
 */
public class TeamRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 20000)
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
