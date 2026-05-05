package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.Series;
import com.yang.lblogserver.domain.SeriesPosts;
import com.yang.lblogserver.mapper.SeriesMapper;
import com.yang.lblogserver.mapper.SeriesPostsMapper;
import com.yang.lblogserver.service.SeriesService;
import com.yang.lblogserver.vo.SeriesVO;
import com.yang.lblogserver.vo.admin.CreateSeriesRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class SeriesServiceImpl implements SeriesService {

    private final SeriesMapper seriesMapper;
    private final SeriesPostsMapper seriesPostsMapper;

    public SeriesServiceImpl(SeriesMapper seriesMapper, SeriesPostsMapper seriesPostsMapper) {
        this.seriesMapper = seriesMapper;
        this.seriesPostsMapper = seriesPostsMapper;
    }

    @Override
    public List<SeriesVO> getSeriesList(int limit, Long categoryId, Long createdBy) {
        return seriesMapper.selectSeriesWithCount(limit, categoryId, createdBy);
    }

    // ---- Admin ----

    @Override
    public Long createSeries(CreateSeriesRequest req, Long createdBy) {
        Series series = new Series();
        series.setTitle(req.getTitle());
        series.setSlug(req.getSlug());
        series.setDescription(req.getDescription());
        series.setCoverImageUrl(req.getCoverImageUrl());
        series.setCategoryId(req.getCategoryId());
        series.setIsCompleted(req.getIsCompleted() != null ? req.getIsCompleted() : 0);
        series.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        series.setCreatedBy(createdBy);
        seriesMapper.insertSeries(series);
        return series.getId();
    }

    @Override
    public void updateSeries(Long id, CreateSeriesRequest req) {
        Series series = new Series();
        series.setId(id);
        series.setTitle(req.getTitle());
        series.setSlug(req.getSlug());
        series.setDescription(req.getDescription());
        series.setCoverImageUrl(req.getCoverImageUrl());
        series.setCategoryId(req.getCategoryId());
        series.setIsCompleted(req.getIsCompleted());
        series.setSortOrder(req.getSortOrder());
        seriesMapper.updateSeries(series);
    }

    @Override
    public void deleteSeries(Long id) {
        seriesMapper.softDeleteSeries(id);
    }

    @Override
    public SeriesVO getSeriesById(Long id) {
        Series s = seriesMapper.selectById(id);
        if (s == null) return null;
        SeriesVO vo = new SeriesVO();
        vo.setId(s.getId());
        vo.setTitle(s.getTitle());
        vo.setSlug(s.getSlug());
        vo.setDescription(s.getDescription());
        vo.setCoverImageUrl(s.getCoverImageUrl());
        vo.setCategoryId(s.getCategoryId());
        vo.setIsCompleted(s.getIsCompleted());
        vo.setSortOrder(s.getSortOrder());
        return vo;
    }

    @Override
    public boolean checkSlug(String slug, Long excludeId) {
        return seriesMapper.countBySlug(slug, excludeId) == 0;
    }

    @Override
    public void linkPosts(Long seriesId, List<Long> postIds) {
        seriesPostsMapper.deleteBySeriesId(seriesId);
        if (postIds.isEmpty()) return;
        List<SeriesPosts> list = new ArrayList<>(postIds.size());
        for (int i = 0; i < postIds.size(); i++) {
            SeriesPosts sp = new SeriesPosts();
            sp.setSeriesId(seriesId);
            sp.setPostId(postIds.get(i));
            sp.setSortOrder(i);
            list.add(sp);
        }
        seriesPostsMapper.insertBatch(list);
    }

    @Override
    public void reorderPosts(Long seriesId, List<Long> postIds) {
        for (int i = 0; i < postIds.size(); i++) {
            seriesPostsMapper.updateSortOrder(seriesId, postIds.get(i), i);
        }
    }
}
