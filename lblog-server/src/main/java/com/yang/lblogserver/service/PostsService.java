package com.yang.lblogserver.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.vo.*;

import java.util.List;

public interface PostsService {

    PageResult<PostVO> getPostList(int page, int pageSize, String sort,
                                   Long categoryId, Long tagId, Long seriesId, String keyword);

    List<HotPostVO> getHotPosts(int limit);

    PostDetailVO getPostBySlug(String slug);

    void reportView(Long id);

    LikeResponseVO likePost(Long postId, String visitorId);

    LikeResponseVO unlikePost(Long postId, String visitorId);

    LikeStatusVO getLikeStatus(Long postId, String visitorId);
}
