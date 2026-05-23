package com.yang.lblogserver.blog.service;

import com.yang.lblogserver.blog.vo.SeriesPostVO;
import com.yang.lblogserver.blog.vo.SeriesVO;
import com.yang.lblogserver.blog.vo.admin.CreateSeriesRequest;

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

    List<SeriesPostVO> getPostsBySeriesId(Long seriesId);

    boolean removePostFromSeries(Long seriesId, Long postId);
}
