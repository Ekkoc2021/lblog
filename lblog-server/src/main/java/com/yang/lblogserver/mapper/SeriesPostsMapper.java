package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.SeriesPosts;
import com.yang.lblogserver.vo.PrevNextPostVO;
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

    List<SeriesPosts> selectByPostIds(List<Long> postIds);
}




