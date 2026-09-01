package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 项目创建/更新请求。
 *
 * <p>{@code name} 项目名，{@code projectKey} 项目键（短标识），{@code description} 描述，
 * {@code status} 状态（active/archived/deleted），{@code enableReview} 是否启用评审流程。
 */
public class ProjectRequest {
    @NotBlank @Size(max = 100)
    private String name;
    @Size(max = 10)
    private String projectKey;
    @Size(max = 20000)
    private String description;
    @Size(max = 20)
    private String status;
    private Boolean enableReview;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Boolean getEnableReview() { return enableReview; }
    public void setEnableReview(Boolean enableReview) { this.enableReview = enableReview; }
}
