package com.yang.lblogserver.blog.mapper;

import com.yang.lblogserver.blog.domain.Posts;
import com.yang.lblogserver.blog.vo.HotPostVO;
import com.yang.lblogserver.blog.vo.admin.AuthorStatisticsVO;
import com.yang.lblogserver.blog.vo.admin.StatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostsMapper {

    List<Posts> selectPostList(@Param("sort") String sort,
                               @Param("categoryId") Long categoryId,
                               @Param("tagId") Long tagId,
                               @Param("seriesId") Long seriesId,
                               @Param("keyword") String keyword,
                               @Param("wLike") Double wLike,
                               @Param("wComment") Double wComment,
                               @Param("wView") Double wView,
                               @Param("decayBase") Integer decayBase,
                               @Param("decayExponent") Double decayExponent);

    List<HotPostVO> selectHotPosts(@Param("limit") int limit);

    Posts selectById(@Param("id") Long id);

    Posts selectBySlug(@Param("slug") String slug);

    int incrementViewCount(@Param("id") Long id);

    int incrementLikeCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);

    @Update("UPDATE posts SET comment_count = comment_count + 1 WHERE id = #{postId}")
    int incrementCommentCount(@Param("postId") Long postId);

    @Update("UPDATE posts SET comment_count = comment_count - 1 WHERE id = #{postId} AND comment_count > 0")
    int decrementCommentCount(@Param("postId") Long postId);

    // ---- Admin ----

    List<Posts> selectPostListAdmin(@Param("status") Integer status,
                                    @Param("keyword") String keyword,
                                    @Param("authorId") Long authorId);

    Posts selectByIdRaw(@Param("id") Long id);

    List<Posts> selectBatchIds(@Param("ids") List<Long> ids);

    int insertPost(Posts post);

    int updatePost(Posts post);

    int softDeletePost(@Param("id") Long id);

    int countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    StatisticsVO selectStatistics();

    // ---- Author Statistics ----

    AuthorStatisticsVO selectAuthorStatistics(@Param("authorId") Long authorId);

    List<AuthorStatisticsVO.StatusItem> selectStatusDistribution(@Param("authorId") Long authorId);

    List<AuthorStatisticsVO.CategoryItem> selectCategoryDistribution(@Param("authorId") Long authorId);

    List<AuthorStatisticsVO.MonthItem> selectMonthlyTrend(@Param("authorId") Long authorId);
}
