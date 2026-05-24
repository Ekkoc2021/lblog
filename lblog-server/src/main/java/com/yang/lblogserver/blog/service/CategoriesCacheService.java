package com.yang.lblogserver.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.blog.mapper.CategoriesMapper;
import com.yang.lblogserver.blog.vo.CategoryVO;
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
public class CategoriesCacheService {

    private static final Logger log = LoggerFactory.getLogger(CategoriesCacheService.class);

    private final CategoriesMapper categoriesMapper;

    private final Cache<Integer, List<CategoryVO>> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10)
            .build();

    public CategoriesCacheService(CategoriesMapper categoriesMapper) {
        this.categoriesMapper = categoriesMapper;
    }

    public List<CategoryVO> getCategoryList(int limit) {
        List<CategoryVO> cached = cache.getIfPresent(limit);
        if (cached != null) {
            log.debug("cache hit: categories(limit={})", limit);
            return cached;
        }

        log.debug("cache miss: categories key={}", limit);
        List<CategoryVO> list = categoriesMapper.selectCategoriesWithCount(limit, null);
        if (list == null) {
            list = Collections.emptyList();
        }
        cache.put(limit, list);
        return list;
    }

    public void refresh() {
        cache.invalidateAll();
        log.debug("cache invalidated: categories");
    }

    @EventListener
    public void onCacheRefresh(CacheRefreshEvent event) {
        if (CacheNames.CATEGORIES.equals(event.cacheName())) {
            refresh();
        }
    }
}
