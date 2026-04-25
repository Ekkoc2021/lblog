package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.mapper.TagsMapper;
import com.yang.lblogserver.service.TagsService;
import com.yang.lblogserver.vo.TagVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagsServiceImpl implements TagsService {

    private final TagsMapper tagsMapper;

    public TagsServiceImpl(TagsMapper tagsMapper) {
        this.tagsMapper = tagsMapper;
    }

    @Override
    public List<TagVO> getTagList(int limit) {
        return tagsMapper.selectTagsWithCount(limit);
    }
}
