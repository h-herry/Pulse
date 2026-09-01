package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.entity.Task;
import com.pulse.entity.TaskAttachment;
import com.pulse.mapper.TaskAttachmentMapper;
import com.pulse.mapper.TaskMapper;
import com.pulse.common.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 任务附件服务（R-027）
 *
 * 文件内容由 Controller 负责落盘（复用 UploadController 的白名单逻辑），
 * 本服务负责记录的增删查与团队归属校验。
 */
@Service
public class AttachmentService {

    private final TaskAttachmentMapper attachmentMapper;
    private final TaskMapper taskMapper;
    private final AuthorizationService authorizationService;

    public AttachmentService(TaskAttachmentMapper attachmentMapper, TaskMapper taskMapper,
                             AuthorizationService authorizationService) {
        this.attachmentMapper = attachmentMapper;
        this.taskMapper = taskMapper;
        this.authorizationService = authorizationService;
    }

    private Task verifyTask(Long taskId, Long teamId) {
        Task t = taskMapper.selectById(taskId);
        if (t == null || !t.getTeamId().equals(teamId)) {
            throw new BusinessException("任务不存在");
        }
        return t;
    }

    public List<TaskAttachment> list(Long taskId, Long teamId) {
        verifyTask(taskId, teamId);
        return attachmentMapper.selectList(new LambdaQueryWrapper<TaskAttachment>()
                .eq(TaskAttachment::getTaskId, taskId)
                .eq(TaskAttachment::getTeamId, teamId)
                .orderByAsc(TaskAttachment::getId));
    }

    @Transactional
    public TaskAttachment create(Long taskId, String fileName, String filePath, long fileSize,
                                 String fileType, Long teamId, Long userId) {
        verifyTask(taskId, teamId);
        TaskAttachment a = new TaskAttachment();
        a.setTaskId(taskId);
        a.setUserId(userId);
        a.setTeamId(teamId);
        a.setFileName(fileName);
        a.setFilePath(filePath);
        a.setFileSize(fileSize);
        a.setFileType(fileType);
        attachmentMapper.insert(a);
        return a;
    }

    public TaskAttachment getAttachment(Long id, Long taskId, Long teamId) {
        TaskAttachment a = attachmentMapper.selectById(id);
        if (a == null || !a.getTeamId().equals(teamId)) {
            throw new BusinessException("附件不存在");
        }
        // 额外按 taskId 过滤，防止跨任务水平越权访问附件
        if (!a.getTaskId().equals(taskId)) {
            throw new BusinessException("附件不存在");
        }
        return a;
    }

    @Transactional
    public void delete(Long id, Long teamId) {
        TaskAttachment a = attachmentMapper.selectById(id);
        if (a == null || !a.getTeamId().equals(teamId)) {
            throw new BusinessException("附件不存在");
        }
        // 权限：附件上传者本人、团队 LEAD 或系统管理员方可删除
        Long currentUserId = TenantContext.getUserId();
        boolean isUploader = currentUserId != null && currentUserId.equals(a.getUserId());
        if (!isUploader) {
            authorizationService.requireTeamLead(teamId);
        }
        attachmentMapper.deleteById(id);
    }
}
