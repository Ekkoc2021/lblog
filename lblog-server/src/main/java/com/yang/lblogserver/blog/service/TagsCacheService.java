package com.yang.lblogserver.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.blog.mapper.TagsMapper;
import com.yang.lblogserver.blog.vo.TagVO;
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
public class TagsCacheService {

    private static final Logger log = LoggerFactory.getLogger(TagsCacheService.class);

    private final TagsMapper tagsMapper;

    private final Cache<Integer, List<TagVO>> cache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10)
            .build();

    public TagsCacheService(TagsMapper tagsMapper) {
        this.tagsMapper = tagsMapper;
    }

    public List<TagVO> getTagList(int limit) {
        List<TagVO> cached = cache.getIfPresent(limit);
        if (cached != null) {
            log.debug("cache hit: tags(limit={})", limit);
            return cached;
        }

        log.debug("cache miss: tags key={}", limit);
        List<TagVO> list = tagsMapper.selectTagsWithCount(limit, null);
        if (list == null) {
            list = Collections.emptyList();
        }
        cache.put(limit, list);
        return list;
    }

    public void refresh() {
        cache.invalidateAll();
        log.debug("cache invalidated: tags");
    }

    @EventListener
    public void onCacheRefresh(CacheRefreshEvent event) {
        if (CacheNames.TAGS.equals(event.cacheName())) {
            refresh();
        }
    }
}
