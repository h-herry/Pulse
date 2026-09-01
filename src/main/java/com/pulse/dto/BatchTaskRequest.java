package com.pulse.dto;

import java.util.List;
import java.util.Map;

/**
 * 批量更新任务请求（R-019）
 *
 * 示例：{ "taskIds": [1,2,3], "updates": { "status": "DONE", "assigneeId": 5 } }
 */
public class BatchTaskRequest {
    private List<Long> taskIds;
    private Map<String, Object> updates;

    public List<Long> getTaskIds() { return taskIds; }
    public void setTaskIds(List<Long> taskIds) { this.taskIds = taskIds; }
    public Map<String, Object> getUpdates() { return updates; }
    public void setUpdates(Map<String, Object> updates) { this.updates = updates; }
}
