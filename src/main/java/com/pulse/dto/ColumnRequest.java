package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 看板列创建/更新请求。
 *
 * <p>{@code name} 列名，{@code wipLimit} 在制品上限（可为空表示不限），
 * {@code mappedStatus} 该列映射的任务状态值（与任务 status 对应）。
 */
public class ColumnRequest {
    @NotBlank @Size(max = 50)
    private String name;
    private Integer wipLimit;
    @NotBlank
    private String mappedStatus;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getWipLimit() { return wipLimit; }
    public void setWipLimit(Integer wipLimit) { this.wipLimit = wipLimit; }
    public String getMappedStatus() { return mappedStatus; }
    public void setMappedStatus(String mappedStatus) { this.mappedStatus = mappedStatus; }
}
