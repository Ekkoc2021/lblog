package com.yang.lblogserver.blog.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yang.lblogserver.blog.mapper.PostsMapper;
import com.yang.lblogserver.blog.vo.HotPostVO;
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
public class HotPostsCacheService {

    private static final Logger log = LoggerFactory.getLogger(HotPostsCacheService.class);

    private final PostsMapper postsMapper;

    private final Cache<Integer, List<HotPostVO>> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5)
            .build();

    public HotPostsCacheService(PostsMapper postsMapper) {
        this.postsMapper = postsMapper;
    }

    public List<HotPostVO> getHotPosts(int limit) {
        List<HotPostVO> cached = cache.getIfPresent(limit);
        if (cached != null) {
            log.debug("cache hit: hotPosts(limit={})", limit);
            return cached;
        }

        log.debug("cache miss: hotPosts key={}", limit);
        List<HotPostVO> list = postsMapper.selectHotPosts(limit);
        if (list == null) {
            list = Collections.emptyList();
        }
        cache.put(limit, list);
        return list;
    }

    public void refresh() {
        cache.invalidateAll();
        log.debug("cache invalidated: hotPosts");
    }

    @EventListener
    public void onCacheRefresh(CacheRefreshEvent event) {
        if (CacheNames.HOT_POSTS.equals(event.cacheName())) {
            refresh();
        }
    }
}
