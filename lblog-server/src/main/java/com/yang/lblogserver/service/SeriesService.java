package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.response.SeriesVO;
import com.yang.lblogserver.vo.admin.CreateSeriesRequest;

import java.util.List;

public interface SeriesService {

    List<SeriesVO> getSeriesList(int limit, Long categoryId, Long createdBy);

    // ---- Admin ----

    Long createSeries(CreateSeriesRequest req, Long createdBy);

    void updateSeries(Long id, CreateSeriesRequest req);

    void deleteSeries(Long id);

    SeriesVO getSeriesById(Long id);

    boolean checkSlug(String slug, Long excludeId);

    void linkPosts(Long seriesId, List<Long> postIds);

    void reorderPosts(Long seriesId, List<Long> postIds);
}
