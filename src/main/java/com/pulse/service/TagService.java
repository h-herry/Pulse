package com.pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pulse.common.BusinessException;
import com.pulse.common.TenantContext;
import com.pulse.dto.TagRequest;
import com.pulse.entity.Tag;
import com.pulse.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 标签服务（R-017）。
 * 负责团队级标签的查询、创建、更新与删除；标签按 teamId 隔离。
 */
@Service
public class TagService {

    private final TagMapper tagMapper;
    private final AuthorizationService authorizationService;

    public TagService(TagMapper tagMapper, AuthorizationService authorizationService) {
        this.tagMapper = tagMapper;
        this.authorizationService = authorizationService;
    }

    /**
     * 列出当前团队的全部标签（按名称升序），基于 TenantContext 隔离。
     */
    public List<Tag> listTags() {
        return tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getTeamId, TenantContext.getTenantId())
                        .orderByAsc(Tag::getName));
    }

    /**
     * 在当前团队创建标签；颜色缺省为 #6b7280。新建标签归属当前团队。
     */
    public Tag createTag(TagRequest req) {
        Tag tag = new Tag();
        tag.setTeamId(TenantContext.getTenantId());
        tag.setName(req.getName());
        tag.setColor(req.getColor() != null ? req.getColor() : "#6b7280");
        tagMapper.insert(tag);
        return tag;
    }

    /**
     * 更新标签名称 / 颜色；校验标签属于当前团队，否则视为不存在。
     */
    public Tag updateTag(Long id, TagRequest req) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("标签不存在");
        }
        tag.setName(req.getName());
        if (req.getColor() != null) tag.setColor(req.getColor());
        tagMapper.updateById(tag);
        return tag;
    }

    /**
     * 删除标签；校验标签属于当前团队，否则视为不存在。
     */
    public void deleteTag(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || !tag.getTeamId().equals(TenantContext.getTenantId())) {
            throw new BusinessException("标签不存在");
        }
        // 破坏性删除：仅团队 LEAD 或系统管理员
        authorizationService.requireTeamLead(TenantContext.getTenantId());
        tagMapper.deleteById(id);
    }
}
