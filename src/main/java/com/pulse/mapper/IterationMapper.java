package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.Iteration;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.Iteration} 的 MyBatis-Plus Mapper，对应表 {@code iterations}。
 * 继承 {@code BaseMapper<Iteration>}，提供迭代的增删改查与分页能力。
 */
public interface IterationMapper extends BaseMapper<Iteration> {
}
