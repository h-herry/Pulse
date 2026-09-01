package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.CustomFieldValue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.CustomFieldValue} 的 MyBatis-Plus Mapper，对应表 {@code custom_field_values}。
 * 继承 {@code BaseMapper<CustomFieldValue>}，提供自定义字段取值的增删改查与分页能力。
 */
public interface CustomFieldValueMapper extends BaseMapper<CustomFieldValue> {
}
