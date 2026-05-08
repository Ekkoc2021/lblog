package com.yang.lblogserver.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.vo.*;
import com.yang.lblogserver.vo.admin.*;

import java.util.List;

public interface PostsService {

    PageResult<PostVO> getPostList(int page, int pageSize, String sort,
                                   Long categoryId, Long tagId, Long seriesId, String keyword);

    List<HotPostVO> getHotPosts(int limit);

    StatisticsVO getStatistics();

    PostDetailVO getPostBySlug(String slug);

    void reportView(Long id);

    LikeResponseVO likePost(Long postId, String visitorId);

    LikeResponseVO unlikePost(Long postId, String visitorId);

    LikeStatusVO getLikeStatus(Long postId, String visitorId);

    // ---- Admin ----

    PageResult<PostVO> getAdminPostList(int page, int pageSize, Integer status, String keyword, Long authorId);

    PostDetailVO getAdminPostById(Long id);

    Long createPost(CreatePostRequest req, Long authorId);

    void updatePost(Long id, UpdatePostRequest req);

    void deletePost(Long id);

    AuthorStatisticsVO getAuthorStatistics(Long authorId);

    boolean checkSlug(String slug, Long excludeId);
}
