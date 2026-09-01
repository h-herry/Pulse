package com.pulse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pulse.entity.BurnDownSnapshot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * {@link com.pulse.entity.BurnDownSnapshot} 的 MyBatis-Plus Mapper，对应表 {@code burn_down_snapshots}。
 * 继承 {@code BaseMapper<BurnDownSnapshot>}，提供燃尽图每日快照的增删改查与分页能力。
 */
public interface BurnDownSnapshotMapper extends BaseMapper<BurnDownSnapshot> {
}
