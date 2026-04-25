package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.SeriesPosts;
import com.yang.lblogserver.vo.PrevNextPostVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeriesPostsMapper {

    SeriesPosts selectByPostId(@Param("postId") Long postId);

    PrevNextPostVO selectPrevPost(@Param("seriesId") Long seriesId, @Param("sortOrder") Integer sortOrder);

    PrevNextPostVO selectNextPost(@Param("seriesId") Long seriesId, @Param("sortOrder") Integer sortOrder);
}




