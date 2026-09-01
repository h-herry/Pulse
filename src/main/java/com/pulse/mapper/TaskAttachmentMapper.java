package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.TaskAttachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.TaskAttachment} 的 MyBatis-Plus Mapper，对应表 {@code task_attachments}。
 * 继承 {@code BaseMapper<TaskAttachment>}，提供任务附件的增删改查与分页能力。
 */
public interface TaskAttachmentMapper extends BaseMapper<TaskAttachment> {
}
