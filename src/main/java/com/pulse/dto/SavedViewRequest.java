package com.pulse.dto;

/**
 * 保存视图请求（R-020）
 *
 * filters / sortConfig 接受任意 JSON 结构（Map / List），由 Service 序列化为 JSON 文本存储。
 */
public class SavedViewRequest {
    private String name;
    private Object filters;
    private Object sortConfig;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Object getFilters() { return filters; }
    public void setFilters(Object filters) { this.filters = filters; }
    public Object getSortConfig() { return sortConfig; }
    public void setSortConfig(Object sortConfig) { this.sortConfig = sortConfig; }
}
