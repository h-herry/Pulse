package com.pulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 自定义字段定义请求（R-028）
 */
public class CustomFieldRequest {
    @NotBlank @Size(max = 50)
    private String name;
    /** 类型：text/number/date/select/multiselect */
    @NotBlank @Size(max = 20)
    private String fieldType;
    /** 选项列表（select/multiselect 使用） */
    @Size(max = 50)
    private List<String> options;
    private Integer sortOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
