package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.TagVO;

import java.util.List;

public interface TagsService {

    List<TagVO> getTagList(int limit);
}
