package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Posts;
import com.yang.lblogserver.vo.HotPostVO;
import com.yang.lblogserver.vo.admin.StatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostsMapper {

    List<Posts> selectPostList(@Param("sort") String sort,
                               @Param("categoryId") Long categoryId,
                               @Param("tagId") Long tagId,
                               @Param("seriesId") Long seriesId,
                               @Param("keyword") String keyword);

    List<HotPostVO> selectHotPosts(@Param("limit") int limit);

    Posts selectById(@Param("id") Long id);

    Posts selectBySlug(@Param("slug") String slug);

    int incrementViewCount(@Param("id") Long id);

    int incrementLikeCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);

    // ---- Admin ----

    List<Posts> selectPostListAdmin(@Param("status") Integer status,
                                    @Param("keyword") String keyword);

    Posts selectByIdRaw(@Param("id") Long id);

    int insertPost(Posts post);

    int updatePost(Posts post);

    int softDeletePost(@Param("id") Long id);

    int countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    StatisticsVO selectStatistics();
}
