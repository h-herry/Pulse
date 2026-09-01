package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.SavedView;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.SavedView} 的 MyBatis-Plus Mapper，对应表 {@code saved_views}。
 * 继承 {@code BaseMapper<SavedView>}，提供保存筛选视图的增删改查与分页能力。
 */
public interface SavedViewMapper extends BaseMapper<SavedView> {
}
