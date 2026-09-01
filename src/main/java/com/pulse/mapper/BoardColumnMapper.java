package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.BoardColumn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.BoardColumn} 的 MyBatis-Plus Mapper，对应表 {@code board_columns}。
 * 继承 {@code BaseMapper<BoardColumn>}，提供看板列的增删改查与分页能力。
 */
public interface BoardColumnMapper extends BaseMapper<BoardColumn> {
}
