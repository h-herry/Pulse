package com.pulse.dto;

import java.util.List;

/**
 * 批量更新任务自定义字段值请求（R-028）
 *
 * 示例：{ "values": [ { "customFieldId": 1, "value": "高优" }, { "customFieldId": 3, "value": "[\"A\",\"B\"]" } ] }
 */
public class CustomFieldValueRequest {
    private List<Item> values;

    public List<Item> getValues() { return values; }
    public void setValues(List<Item> values) { this.values = values; }

    public static class Item {
        private Long customFieldId;
        private String value;

        public Long getCustomFieldId() { return customFieldId; }
        public void setCustomFieldId(Long customFieldId) { this.customFieldId = customFieldId; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
