package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.Tag} 的 MyBatis-Plus Mapper，对应表 {@code tags}。
 * 继承 {@code BaseMapper<Tag>}，提供标签的增删改查与分页能力。
 */
public interface TagMapper extends BaseMapper<Tag> {
}
