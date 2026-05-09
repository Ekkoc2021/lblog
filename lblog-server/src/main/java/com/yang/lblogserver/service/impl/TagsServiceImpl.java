package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.Tags;
import com.yang.lblogserver.mapper.TagsMapper;
import com.yang.lblogserver.service.TagsService;
import com.yang.lblogserver.vo.TagVO;
import com.yang.lblogserver.vo.admin.CreateTagRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagsServiceImpl implements TagsService {

    private final TagsMapper tagsMapper;

    public TagsServiceImpl(TagsMapper tagsMapper) {
        this.tagsMapper = tagsMapper;
    }

    @Override
    public List<TagVO> getTagList(int limit, Long createdBy) {
        return tagsMapper.selectTagsWithCount(limit, createdBy);
    }

    // ---- Admin ----

    @Override
    public boolean checkSlug(String slug, Long excludeId) {
        return tagsMapper.countBySlug(slug, excludeId) == 0;
    }

    @Override
    public Long createTag(CreateTagRequest req, Long createdBy) {
        Tags tag = new Tags();
        tag.setName(req.getName());
        tag.setSlug(req.getSlug());
        tag.setCreatedBy(createdBy);
        tagsMapper.insertTag(tag);
        return tag.getId();
    }

    @Override
    public void updateTag(Long id, CreateTagRequest req) {
        Tags tag = new Tags();
        tag.setId(id);
        tag.setName(req.getName());
        tag.setSlug(req.getSlug());
        tagsMapper.updateTag(tag);
    }

    @Override
    public void deleteTag(Long id) {
        tagsMapper.softDeleteTag(id);
    }

    @Override
    public TagVO getTagById(Long id) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return null;
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }
}
