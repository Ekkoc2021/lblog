package com.yang.lblogserver.blog.mapper;

import com.yang.lblogserver.blog.domain.SeriesPosts;
import com.yang.lblogserver.blog.vo.PrevNextPostVO;
import com.yang.lblogserver.blog.vo.SeriesPostVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeriesPostsMapper {

    SeriesPosts selectByPostId(@Param("postId") Long postId);

    PrevNextPostVO selectPrevPost(@Param("seriesId") Long seriesId, @Param("sortOrder") Integer sortOrder);

    PrevNextPostVO selectNextPost(@Param("seriesId") Long seriesId, @Param("sortOrder") Integer sortOrder);

    // ---- Admin ----

    int deleteBySeriesId(@Param("seriesId") Long seriesId);

    int insertBatch(List<SeriesPosts> list);

    int deleteByPostId(@Param("postId") Long postId);

    int insert(SeriesPosts seriesPosts);

    int updateSortOrder(@Param("seriesId") Long seriesId,
                         @Param("postId") Long postId,
                         @Param("sortOrder") Integer sortOrder);

    int selectMaxSortOrder(@Param("seriesId") Long seriesId);

    List<SeriesPosts> selectByPostIds(List<Long> postIds);

    List<SeriesPostVO> selectPostsBySeriesId(@Param("seriesId") Long seriesId);

    int deleteBySeriesIdAndPostId(@Param("seriesId") Long seriesId, @Param("postId") Long postId);
}




