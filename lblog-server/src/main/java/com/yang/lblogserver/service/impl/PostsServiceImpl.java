package com.yang.lblogserver.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.*;
import com.yang.lblogserver.mapper.*;
import com.yang.lblogserver.service.PostContentsService;
import com.yang.lblogserver.service.PostsService;
import com.yang.lblogserver.vo.response.CategoryVO;
import com.yang.lblogserver.vo.response.HotPostVO;
import com.yang.lblogserver.vo.response.LikeResponseVO;
import com.yang.lblogserver.vo.response.LikeStatusVO;
import com.yang.lblogserver.vo.response.PostDetailVO;
import com.yang.lblogserver.vo.response.PostVO;
import com.yang.lblogserver.vo.response.SeriesVO;
import com.yang.lblogserver.vo.response.TagVO;
import com.yang.lblogserver.vo.admin.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostsServiceImpl implements PostsService {

    private final PostsMapper postsMapper;
    private final UsersMapper usersMapper;
    private final CategoriesMapper categoriesMapper;
    private final PostTagsMapper postTagsMapper;
    private final TagsMapper tagsMapper;
    private final PostContentsService postContentsService;
    private final LikeRecordsMapper likeRecordsMapper;
    private final SeriesPostsMapper seriesPostsMapper;
    private final SeriesMapper seriesMapper;

    public PostsServiceImpl(PostsMapper postsMapper, UsersMapper usersMapper,
                            CategoriesMapper categoriesMapper, PostTagsMapper postTagsMapper,
                            TagsMapper tagsMapper, PostContentsService postContentsService,
                            LikeRecordsMapper likeRecordsMapper,
                            SeriesPostsMapper seriesPostsMapper,
                            SeriesMapper seriesMapper) {
        this.postsMapper = postsMapper;
        this.usersMapper = usersMapper;
        this.categoriesMapper = categoriesMapper;
        this.postTagsMapper = postTagsMapper;
        this.tagsMapper = tagsMapper;
        this.postContentsService = postContentsService;
        this.likeRecordsMapper = likeRecordsMapper;
        this.seriesPostsMapper = seriesPostsMapper;
        this.seriesMapper = seriesMapper;
    }

    @Override
    public PageResult<PostVO> getPostList(int page, int pageSize, String sort,
                                          Long categoryId, Long tagId, Long seriesId, String keyword) {
        if (!Arrays.asList("recommend", "newest", "hot").contains(sort)) {
            sort = "recommend";
        }

        PageHelper.startPage(page, pageSize);
        List<Posts> posts = postsMapper.selectPostList(sort, categoryId, tagId, seriesId, keyword);
        PageInfo<Posts> pageInfo = new PageInfo<>(posts);

        if (posts.isEmpty()) {
            return PageResult.of(page, pageSize, 0, Collections.emptyList());
        }

        Set<Long> authorIds = posts.stream().map(Posts::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> categoryIds = posts.stream().map(Posts::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> postIds = posts.stream().map(Posts::getId).collect(Collectors.toList());

        Map<Long, Users> userMap = authorIds.isEmpty() ? Collections.emptyMap() :
                usersMapper.selectBatchIds(new ArrayList<>(authorIds)).stream()
                        .collect(Collectors.toMap(Users::getId, u -> u));

        Map<Long, Categories> catMap = categoryIds.isEmpty() ? Collections.emptyMap() :
                categoriesMapper.selectBatchIds(new ArrayList<>(categoryIds)).stream()
                        .collect(Collectors.toMap(Categories::getId, c -> c));

        List<PostTags> postTagsList = postTagsMapper.selectByPostIds(postIds);
        Set<Long> tagIds = postTagsList.stream().map(PostTags::getTagId).collect(Collectors.toSet());
        Map<Long, Tags> tagMap = tagIds.isEmpty() ? Collections.emptyMap() :
                tagsMapper.selectBatchIds(new ArrayList<>(tagIds)).stream()
                        .collect(Collectors.toMap(Tags::getId, t -> t));

        Map<Long, List<TagVO>> postTagMap = new HashMap<>();
        for (PostTags pt : postTagsList) {
            Tags tag = tagMap.get(pt.getTagId());
            if (tag != null) {
                TagVO tagVO = new TagVO();
                tagVO.setId(tag.getId());
                tagVO.setName(tag.getName());
                tagVO.setSlug(tag.getSlug());
                postTagMap.computeIfAbsent(pt.getPostId(), k -> new ArrayList<>()).add(tagVO);
            }
        }

        List<PostVO> voList = new ArrayList<>(posts.size());
        for (Posts post : posts) {
            PostVO vo = buildPostVO(post, userMap, catMap, postTagMap);
            voList.add(vo);
        }

        return PageResult.of(page, pageSize, pageInfo.getTotal(), voList);
    }

    @Override
    public List<HotPostVO> getHotPosts(int limit) {
        return postsMapper.selectHotPosts(limit);
    }

    @Override
    public PostDetailVO getPostBySlug(String slug) {
        Posts post = postsMapper.selectBySlug(slug);
        if (post == null) {
            return null;
        }

        PostContents contents = postContentsService.getByPostId(post.getId());
        PostDetailVO vo = new PostDetailVO();
        copyPostToVO(post, vo);
        vo.setBody(contents != null ? contents.getBody() : null);

        // 填充作者、分类、标签
        assemblePostRelations(post, vo);

        // 查询上下篇（同专栏内按 sort_order 排序）
        SeriesPosts seriesLink = seriesPostsMapper.selectByPostId(post.getId());
        if (seriesLink != null) {
            vo.setPrevPost(seriesPostsMapper.selectPrevPost(seriesLink.getSeriesId(), seriesLink.getSortOrder()));
            vo.setNextPost(seriesPostsMapper.selectNextPost(seriesLink.getSeriesId(), seriesLink.getSortOrder()));
        }

        return vo;
    }

    @Override
    public void reportView(Long id) {
        postsMapper.incrementViewCount(id);
    }

    @Override
    public LikeResponseVO likePost(Long postId, String visitorId) {
        Posts post = postsMapper.selectById(postId);
        if (post == null) {
            return null;
        }

        if (likeRecordsMapper.existsByPostIdAndVisitorId(postId, visitorId) > 0) {
            return new LikeResponseVO(true, post.getLikeCount() != null ? post.getLikeCount() : 0);
        }

        likeRecordsMapper.insert(postId, visitorId);
        postsMapper.incrementLikeCount(postId);
        return new LikeResponseVO(true, (post.getLikeCount() != null ? post.getLikeCount() : 0) + 1);
    }

    @Override
    public LikeResponseVO unlikePost(Long postId, String visitorId) {
        int deleted = likeRecordsMapper.deleteByPostIdAndVisitorId(postId, visitorId);
        if (deleted > 0) {
            postsMapper.decrementLikeCount(postId);
        }
        Posts post = postsMapper.selectById(postId);
        return new LikeResponseVO(false, post != null && post.getLikeCount() != null ? post.getLikeCount() : 0);
    }

    @Override
    public LikeStatusVO getLikeStatus(Long postId, String visitorId) {
        boolean liked = likeRecordsMapper.existsByPostIdAndVisitorId(postId, visitorId) > 0;
        return new LikeStatusVO(liked);
    }

    private PostVO buildPostVO(Posts post, Map<Long, Users> userMap,
                               Map<Long, Categories> catMap,
                               Map<Long, List<TagVO>> postTagMap) {
        PostVO vo = new PostVO();
        copyPostToVO(post, vo);

        Users user = userMap.get(post.getAuthorId());
        if (user != null) {
            PostVO.AuthorVO authorVO = new PostVO.AuthorVO();
            authorVO.setId(user.getId());
            authorVO.setNickname(user.getNickname());
            authorVO.setAvatar(user.getAvatar());
            vo.setAuthor(authorVO);
        }

        Categories cat = catMap.get(post.getCategoryId());
        if (cat != null) {
            CategoryVO catVO = new CategoryVO();
            catVO.setId(cat.getId());
            catVO.setName(cat.getName());
            catVO.setSlug(cat.getSlug());
            vo.setCategory(catVO);
        }

        vo.setTags(postTagMap.getOrDefault(post.getId(), Collections.emptyList()));
        return vo;
    }

    private void copyPostToVO(Posts post, PostVO vo) {
        vo.setId(post.getId());
        vo.setTitle(post.getTitle());
        vo.setSlug(post.getSlug());
        vo.setExcerpt(post.getExcerpt());
        vo.setFeaturedImage(post.getFeaturedImage());
        vo.setStatus(post.getStatus());
        vo.setAuthorId(post.getAuthorId());
        vo.setCategoryId(post.getCategoryId());
        vo.setPublishedAt(post.getPublishedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setUpdatedAt(post.getUpdatedAt());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setCommentEnable(post.getCommentEnable());
    }

    private void assemblePostRelations(Posts post, PostVO vo) {
        // 作者
        List<Users> users = usersMapper.selectBatchIds(Collections.singletonList(post.getAuthorId()));
        if (!users.isEmpty()) {
            Users user = users.get(0);
            PostVO.AuthorVO authorVO = new PostVO.AuthorVO();
            authorVO.setId(user.getId());
            authorVO.setNickname(user.getNickname());
            authorVO.setAvatar(user.getAvatar());
            vo.setAuthor(authorVO);
        }

        // 分类
        if (post.getCategoryId() != null) {
            List<Categories> cats = categoriesMapper.selectBatchIds(Collections.singletonList(post.getCategoryId()));
            if (!cats.isEmpty()) {
                Categories cat = cats.get(0);
                CategoryVO catVO = new CategoryVO();
                catVO.setId(cat.getId());
                catVO.setName(cat.getName());
                catVO.setSlug(cat.getSlug());
                vo.setCategory(catVO);
            }
        }

        // 标签
        List<PostTags> postTagsList = postTagsMapper.selectByPostIds(Collections.singletonList(post.getId()));
        if (!postTagsList.isEmpty()) {
            Set<Long> tagIds = postTagsList.stream().map(PostTags::getTagId).collect(Collectors.toSet());
            Map<Long, Tags> tagMap = tagsMapper.selectBatchIds(new ArrayList<>(tagIds)).stream()
                    .collect(Collectors.toMap(Tags::getId, t -> t));
            List<TagVO> tagVOs = new ArrayList<>(tagIds.size());
            for (PostTags pt : postTagsList) {
                Tags tag = tagMap.get(pt.getTagId());
                if (tag != null) {
                    TagVO tagVO = new TagVO();
                    tagVO.setId(tag.getId());
                    tagVO.setName(tag.getName());
                    tagVO.setSlug(tag.getSlug());
                    tagVOs.add(tagVO);
                }
            }
            vo.setTags(tagVOs);
        } else {
            vo.setTags(Collections.emptyList());
        }
    }

    // ========== Admin ==========

    @Override
    public PageResult<PostVO> getAdminPostList(int page, int pageSize, Integer status, String keyword, Long authorId) {
        PageHelper.startPage(page, pageSize);
        List<Posts> posts = postsMapper.selectPostListAdmin(status, keyword, authorId);
        PageInfo<Posts> pageInfo = new PageInfo<>(posts);

        if (posts.isEmpty()) {
            return PageResult.of(page, pageSize, 0, Collections.emptyList());
        }

        Set<Long> authorIds = posts.stream().map(Posts::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> categoryIds = posts.stream().map(Posts::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> postIds = posts.stream().map(Posts::getId).collect(Collectors.toList());

        Map<Long, Users> userMap = authorIds.isEmpty() ? Collections.emptyMap() :
                usersMapper.selectBatchIds(new ArrayList<>(authorIds)).stream()
                        .collect(Collectors.toMap(Users::getId, u -> u));
        Map<Long, Categories> catMap = categoryIds.isEmpty() ? Collections.emptyMap() :
                categoriesMapper.selectBatchIds(new ArrayList<>(categoryIds)).stream()
                        .collect(Collectors.toMap(Categories::getId, c -> c));

        List<PostTags> postTagsList = postTagsMapper.selectByPostIds(postIds);
        Set<Long> tagIds = postTagsList.stream().map(PostTags::getTagId).collect(Collectors.toSet());
        Map<Long, Tags> tagMap = tagIds.isEmpty() ? Collections.emptyMap() :
                tagsMapper.selectBatchIds(new ArrayList<>(tagIds)).stream()
                        .collect(Collectors.toMap(Tags::getId, t -> t));

        Map<Long, List<TagVO>> postTagMap = new HashMap<>();
        for (PostTags pt : postTagsList) {
            Tags tag = tagMap.get(pt.getTagId());
            if (tag != null) {
                TagVO tagVO = new TagVO();
                tagVO.setId(tag.getId());
                tagVO.setName(tag.getName());
                tagVO.setSlug(tag.getSlug());
                postTagMap.computeIfAbsent(pt.getPostId(), k -> new ArrayList<>()).add(tagVO);
            }
        }

        // 查询专栏关联
        List<SeriesPosts> seriesPostsList = seriesPostsMapper.selectByPostIds(postIds);
        Map<Long, Series> seriesMap = new HashMap<>();
        if (!seriesPostsList.isEmpty()) {
            Set<Long> seriesIds = seriesPostsList.stream().map(SeriesPosts::getSeriesId).collect(Collectors.toSet());
            if (!seriesIds.isEmpty()) {
                seriesMap = seriesMapper.selectBatchIds(new ArrayList<>(seriesIds)).stream()
                        .collect(Collectors.toMap(Series::getId, s -> s));
            }
        }
        Map<Long, SeriesVO> postSeriesMap = new HashMap<>();
        for (SeriesPosts sp : seriesPostsList) {
            Series s = seriesMap.get(sp.getSeriesId());
            if (s != null) {
                SeriesVO svo = new SeriesVO();
                svo.setId(s.getId());
                svo.setTitle(s.getTitle());
                svo.setSlug(s.getSlug());
                postSeriesMap.put(sp.getPostId(), svo);
            }
        }

        List<PostVO> voList = new ArrayList<>(posts.size());
        for (Posts post : posts) {
            PostVO vo = buildPostVO(post, userMap, catMap, postTagMap);
            vo.setSeries(postSeriesMap.get(post.getId()));
            voList.add(vo);
        }

        return PageResult.of(page, pageSize, pageInfo.getTotal(), voList);
    }

    @Override
    public PostDetailVO getAdminPostById(Long id) {
        Posts post = postsMapper.selectByIdRaw(id);
        if (post == null) {
            return null;
        }

        PostContents contents = postContentsService.getByPostId(post.getId());
        PostDetailVO vo = new PostDetailVO();
        copyPostToVO(post, vo);
        vo.setBody(contents != null ? contents.getBody() : null);

        // 填充作者、分类、标签
        assemblePostRelations(post, vo);

        // 填充专栏
        SeriesPosts seriesLink = seriesPostsMapper.selectByPostId(post.getId());
        if (seriesLink != null) {
            Series s = seriesMapper.selectById(seriesLink.getSeriesId());
            if (s != null) {
                SeriesVO svo = new SeriesVO();
                svo.setId(s.getId());
                svo.setTitle(s.getTitle());
                svo.setSlug(s.getSlug());
                vo.setSeries(svo);
            }
        }

        return vo;
    }

    @Override
    public Long createPost(CreatePostRequest req, Long authorId) {
        Posts post = new Posts();
        post.setTitle(req.getTitle());
        post.setSlug(req.getSlug());
        // 摘要：不传则从 body 截取前 200 字
        String excerpt = req.getExcerpt();
        if (excerpt == null || excerpt.isBlank()) {
            excerpt = req.getBody().length() > 200
                    ? req.getBody().substring(0, 200).replaceAll("[#*`>\\[\\]]", "").strip()
                    : req.getBody().replaceAll("[#*`>\\[\\]]", "").strip();
        }
        post.setExcerpt(excerpt);
        post.setFeaturedImage(req.getFeaturedImage());
        post.setStatus(req.getStatus());
        post.setAuthorId(authorId);
        post.setCategoryId(req.getCategoryId());
        post.setCommentEnable(req.getCommentEnable() != null ? req.getCommentEnable() : 1);

        // 发布时自动设发布时间
        if (req.getStatus() == 1) {
            post.setPublishedAt(new Date());
        }

        postsMapper.insertPost(post);
        Long postId = post.getId();

        // 写入正文
        postContentsService.save(postId, req.getBody(), "markdown");

        // 写入标签关联
        if (req.getTagIds() != null && !req.getTagIds().isEmpty()) {
            List<PostTags> tagList = req.getTagIds().stream()
                    .map(tagId -> {
                        PostTags pt = new PostTags();
                        pt.setPostId(postId);
                        pt.setTagId(tagId);
                        return pt;
                    })
                    .collect(Collectors.toList());
            postTagsMapper.insertBatch(tagList);
        }

        // 写入专栏关联
        if (req.getSeriesId() != null) {
            SeriesPosts sp = new SeriesPosts();
            sp.setSeriesId(req.getSeriesId());
            sp.setPostId(postId);
            sp.setSortOrder(0);
            seriesPostsMapper.insert(sp);
        }

        return postId;
    }

    @Override
    public void updatePost(Long id, UpdatePostRequest req) {
        Posts post = postsMapper.selectByIdRaw(id);
        if (post == null) {
            throw new RuntimeException("文章不存在");
        }

        Posts update = new Posts();
        update.setId(id);
        update.setTitle(req.getTitle());
        update.setSlug(req.getSlug());
        update.setExcerpt(req.getExcerpt());
        update.setFeaturedImage(req.getFeaturedImage());
        update.setCategoryId(req.getCategoryId());
        update.setCommentEnable(req.getCommentEnable());

        // 如果从非发布变为发布，设发布时间
        if (req.getStatus() != null && req.getStatus() == 1 && !Integer.valueOf(1).equals(post.getStatus())) {
            update.setPublishedAt(new Date());
        }
        update.setStatus(req.getStatus());

        postsMapper.updatePost(update);

        // 更新正文
        if (req.getBody() != null) {
            postContentsService.saveOrUpdate(id, req.getBody());
        }

        // 更新标签（先删后插）
        if (req.getTagIds() != null) {
            postTagsMapper.deleteByPostId(id);
            if (!req.getTagIds().isEmpty()) {
                List<PostTags> tagList = req.getTagIds().stream()
                        .map(tagId -> {
                            PostTags pt = new PostTags();
                            pt.setPostId(id);
                            pt.setTagId(tagId);
                            return pt;
                        })
                        .collect(Collectors.toList());
                postTagsMapper.insertBatch(tagList);
            }
        }

        // 更新专栏关联
        if (req.getSeriesId() != null) {
            seriesPostsMapper.deleteByPostId(id);
            SeriesPosts sp = new SeriesPosts();
            sp.setSeriesId(req.getSeriesId());
            sp.setPostId(id);
            sp.setSortOrder(0);
            seriesPostsMapper.insert(sp);
        }

    }

    @Override
    public void deletePost(Long id) {
        postsMapper.softDeletePost(id);
        postTagsMapper.deleteByPostId(id);
        seriesPostsMapper.deleteByPostId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthorStatisticsVO getAuthorStatistics(Long authorId) {
        AuthorStatisticsVO stats = postsMapper.selectAuthorStatistics(authorId);
        if (stats == null) {
            stats = new AuthorStatisticsVO();
        }

        // 状态分布
        List<AuthorStatisticsVO.StatusItem> statusDist = postsMapper.selectStatusDistribution(authorId);
        stats.setStatusDistribution(statusDist != null ? statusDist : Collections.emptyList());

        // 分类分布
        List<AuthorStatisticsVO.CategoryItem> catDist = postsMapper.selectCategoryDistribution(authorId);
        stats.setCategoryDistribution(catDist != null ? catDist : Collections.emptyList());

        // 月度趋势：查询 + 补足 12 个月
        List<AuthorStatisticsVO.MonthItem> dbTrend = postsMapper.selectMonthlyTrend(authorId);
        Map<String, Integer> monthMap = new HashMap<>();
        if (dbTrend != null) {
            for (AuthorStatisticsVO.MonthItem item : dbTrend) {
                monthMap.put(item.getMonth(), item.getCount());
            }
        }

        List<AuthorStatisticsVO.MonthItem> fullTrend = new ArrayList<>(12);
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        for (int i = 11; i >= 0; i--) {
            Calendar m = Calendar.getInstance();
            m.setTime(new Date());
            m.add(Calendar.MONTH, -i);
            String month = String.format("%04d-%02d", m.get(Calendar.YEAR), m.get(Calendar.MONTH) + 1);
            int count = monthMap.getOrDefault(month, 0);
            AuthorStatisticsVO.MonthItem item = new AuthorStatisticsVO.MonthItem();
            item.setMonth(month);
            item.setCount(count);
            fullTrend.add(item);
        }
        stats.setMonthlyTrend(fullTrend);

        return stats;
    }

    @Override
    public boolean checkSlug(String slug, Long excludeId) {
        return postsMapper.countBySlug(slug, excludeId) == 0;
    }

    @Override
    public StatisticsVO getStatistics() {
        StatisticsVO stats = postsMapper.selectStatistics();
        if (stats == null) {
            stats = new StatisticsVO();
        }

        // 分类分布
        List<StatisticsVO.CategoryDist> catDist = categoriesMapper.selectCategoriesWithCount(null, null)
                .stream()
                .map(c -> {
                    StatisticsVO.CategoryDist d = new StatisticsVO.CategoryDist();
                    d.setName(c.getName());
                    d.setCount(c.getPostCount() != null ? c.getPostCount() : 0);
                    return d;
                })
                .collect(Collectors.toList());
        stats.setCategoryDistribution(catDist);

        // 标签分布
        List<StatisticsVO.TagDist> tagDist = tagsMapper.selectTagsWithCount(100, null)
                .stream()
                .map(t -> {
                    StatisticsVO.TagDist d = new StatisticsVO.TagDist();
                    d.setName(t.getName());
                    d.setCount(t.getPostCount() != null ? t.getPostCount() : 0);
                    return d;
                })
                .collect(Collectors.toList());
        stats.setTagDistribution(tagDist);

        return stats;
    }
}
