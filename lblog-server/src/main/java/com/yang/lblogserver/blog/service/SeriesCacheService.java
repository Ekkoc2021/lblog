package com.yang.lblogserver.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.blog.mapper.SeriesMapper;
import com.yang.lblogserver.blog.vo.SeriesVO;
import com.yang.lblogserver.common.cache.constant.CacheNames;
import com.yang.lblogserver.common.cache.event.CacheRefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeriesCacheService {

    private static final Logger log = LoggerFactory.getLogger(SeriesCacheService.class);

    private final SeriesMapper seriesMapper;

    private final Cache<String, List<SeriesVO>> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10)
            .build();

    public SeriesCacheService(SeriesMapper seriesMapper) {
        this.seriesMapper = seriesMapper;
    }

    public List<SeriesVO> getSeriesList(int limit, Long categoryId) {
        String key = limit + "_" + categoryId;
        List<SeriesVO> cached = cache.getIfPresent(key);
        if (cached != null) {
            log.debug("cache hit: series(limit={}, categoryId={})", limit, categoryId);
            return cached;
        }

        log.debug("cache miss: series key={}", key);
        List<SeriesVO> list = seriesMapper.selectSeriesWithCount(limit, categoryId, null);
        if (list == null) {
            list = Collections.emptyList();
        }
        cache.put(key, list);
        return list;
    }

    public void refresh() {
        cache.invalidateAll();
        log.debug("cache invalidated: series");
    }

    @EventListener
    public void onCacheRefresh(CacheRefreshEvent event) {
        if (CacheNames.SERIES.equals(event.cacheName())) {
            refresh();
        }
    }
}
