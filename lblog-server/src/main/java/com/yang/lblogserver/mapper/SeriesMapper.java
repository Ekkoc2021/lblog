package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Series;
import com.yang.lblogserver.vo.SeriesVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeriesMapper {

    List<SeriesVO> selectSeriesWithCount(@Param("limit") int limit,
                                         @Param("categoryId") Long categoryId);

    // ---- Admin ----

    int insertSeries(Series series);

    int updateSeries(Series series);

    int softDeleteSeries(@Param("id") Long id);

    int countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

    Series selectById(@Param("id") Long id);

    List<Series> selectBatchIds(List<Long> ids);
}
