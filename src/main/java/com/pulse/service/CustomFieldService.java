package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.BusinessException;
import com.pulse.dto.CustomFieldRequest;
import com.pulse.dto.CustomFieldValueRequest;
import com.pulse.entity.CustomField;
import com.pulse.entity.CustomFieldValue;
import com.pulse.entity.Project;
import com.pulse.entity.Task;
import com.pulse.mapper.CustomFieldMapper;
import com.pulse.mapper.CustomFieldValueMapper;
import com.pulse.mapper.ProjectMapper;
import com.pulse.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义字段服务（R-028）
 *
 * 字段定义按项目维护，取值按任务 upsert。所有方法校验团队归属。
 */
@Service
public class CustomFieldService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CustomFieldMapper fieldMapper;
    private final CustomFieldValueMapper valueMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;

    public CustomFieldService(CustomFieldMapper fieldMapper, CustomFieldValueMapper valueMapper,
                              ProjectMapper projectMapper, TaskMapper taskMapper) {
        this.fieldMapper = fieldMapper;
        this.valueMapper = valueMapper;
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
    }

    private void verifyProject(Long projectId, Long teamId) {
        Project p = projectMapper.selectById(projectId);
        if (p == null || !p.getTeamId().equals(teamId)) {
            throw new BusinessException("项目不存在");
        }
    }

    private void verifyTask(Long taskId, Long teamId) {
        Task t = taskMapper.selectById(taskId);
        if (t == null || !t.getTeamId().equals(teamId)) {
            throw new BusinessException("任务不存在");
        }
    }

    public List<CustomField> listFields(Long projectId, Long teamId) {
        verifyProject(projectId, teamId);
        return fieldMapper.selectList(new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getProjectId, projectId)
                .eq(CustomField::getTeamId, teamId)
                .orderByAsc(CustomField::getSortOrder)
                .orderByAsc(CustomField::getId));
    }

    @Transactional
    public CustomField createField(Long projectId, CustomFieldRequest req, Long teamId) {
        verifyProject(projectId, teamId);
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException("字段名称不能为空");
        }
        int order = req.getSortOrder() != null ? req.getSortOrder() :
                fieldMapper.selectCount(new LambdaQueryWrapper<CustomField>().eq(CustomField::getProjectId, projectId)).intValue();
        CustomField f = new CustomField();
        f.setProjectId(projectId);
        f.setTeamId(teamId);
        f.setName(req.getName().trim());
        f.setFieldType(req.getFieldType() != null ? req.getFieldType() : "text");
        f.setOptions(toJson(req.getOptions()));
        f.setSortOrder(order);
        fieldMapper.insert(f);
        return f;
    }

    @Transactional
    public CustomField updateField(Long id, CustomFieldRequest req, Long teamId) {
        CustomField f = fieldMapper.selectById(id);
        if (f == null || !f.getTeamId().equals(teamId)) {
            throw new BusinessException("自定义字段不存在");
        }
        if (req.getName() != null) f.setName(req.getName().trim());
        if (req.getFieldType() != null) f.setFieldType(req.getFieldType());
        if (req.getOptions() != null) f.setOptions(toJson(req.getOptions()));
        if (req.getSortOrder() != null) f.setSortOrder(req.getSortOrder());
        fieldMapper.updateById(f);
        return f;
    }

    @Transactional
    public void deleteField(Long id, Long teamId) {
        CustomField f = fieldMapper.selectById(id);
        if (f == null || !f.getTeamId().equals(teamId)) {
            throw new BusinessException("自定义字段不存在");
        }
        // 级联删除该字段在所有任务上的取值
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>().eq(CustomFieldValue::getCustomFieldId, id));
        fieldMapper.deleteById(id);
    }

    /**
     * 批量更新任务的自定义字段值（upsert）。
     */
    @Transactional
    public int updateValues(Long taskId, CustomFieldValueRequest req, Long teamId) {
        verifyTask(taskId, teamId);
        if (req.getValues() == null) return 0;
        int count = 0;
        for (CustomFieldValueRequest.Item item : req.getValues()) {
            if (item.getCustomFieldId() == null) continue;
            CustomField field = fieldMapper.selectById(item.getCustomFieldId());
            if (field == null || !field.getTeamId().equals(teamId)) continue;
            CustomFieldValue existing = valueMapper.selectOne(new LambdaQueryWrapper<CustomFieldValue>()
                    .eq(CustomFieldValue::getTaskId, taskId)
                    .eq(CustomFieldValue::getCustomFieldId, item.getCustomFieldId()));
            if (existing != null) {
                existing.setValue(item.getValue());
                valueMapper.updateById(existing);
            } else {
                CustomFieldValue v = new CustomFieldValue();
                v.setTaskId(taskId);
                v.setCustomFieldId(item.getCustomFieldId());
                v.setTeamId(teamId);
                v.setValue(item.getValue());
                valueMapper.insert(v);
            }
            count++;
        }
        return count;
    }

    /**
     * 返回任务的自定义字段（含当前取值），供前端编辑器渲染。
     */
    public List<Map<String, Object>> getValuesWithFields(Long taskId, Long teamId) {
        verifyTask(taskId, teamId);
        Task t = taskMapper.selectById(taskId);
        List<CustomField> fields = fieldMapper.selectList(new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getProjectId, t.getProjectId())
                .eq(CustomField::getTeamId, teamId)
                .orderByAsc(CustomField::getSortOrder)
                .orderByAsc(CustomField::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CustomField f : fields) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("fieldType", f.getFieldType());
            m.put("options", parseOptions(f.getOptions()));
            CustomFieldValue v = valueMapper.selectOne(new LambdaQueryWrapper<CustomFieldValue>()
                    .eq(CustomFieldValue::getTaskId, taskId)
                    .eq(CustomFieldValue::getCustomFieldId, f.getId()));
            m.put("value", v != null ? v.getValue() : null);
            result.add(m);
        }
        return result;
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, List.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
