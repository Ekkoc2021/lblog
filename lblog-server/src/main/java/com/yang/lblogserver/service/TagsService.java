package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.TagVO;
import com.yang.lblogserver.vo.admin.CreateTagRequest;

import java.util.List;

public interface TagsService {

    List<TagVO> getTagList(int limit, Long createdBy);

    // ---- Admin ----

    boolean checkSlug(String slug, Long excludeId);

    Long createTag(CreateTagRequest req, Long createdBy);

    void updateTag(Long id, CreateTagRequest req);

    void deleteTag(Long id);

    TagVO getTagById(Long id);
}

