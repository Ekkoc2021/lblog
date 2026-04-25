package com.yang.lblogserver.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.vo.HotPostVO;
import com.yang.lblogserver.vo.PostVO;

import java.util.List;

public interface PostsService {

    PageResult<PostVO> getPostList(int page, int pageSize, String sort,
                                   Long categoryId, Long tagId, Long seriesId, String keyword);

    List<HotPostVO> getHotPosts(int limit);
}
