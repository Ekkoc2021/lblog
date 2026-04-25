package com.yang.lblogserver.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.*;
import com.yang.lblogserver.mapper.*;
import com.yang.lblogserver.service.PostsService;
import com.yang.lblogserver.vo.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostsServiceImpl implements PostsService {

    private final PostsMapper postsMapper;
    private final UsersMapper usersMapper;
    private final CategoriesMapper categoriesMapper;
    private final PostTagsMapper postTagsMapper;
    private final TagsMapper tagsMapper;
    private final PostContentsMapper postContentsMapper;
    private final LikeRecordsMapper likeRecordsMapper;
    private final SeriesPostsMapper seriesPostsMapper;

    public PostsServiceImpl(PostsMapper postsMapper, UsersMapper usersMapper,
                            CategoriesMapper categoriesMapper, PostTagsMapper postTagsMapper,
                            TagsMapper tagsMapper, PostContentsMapper postContentsMapper,
                            LikeRecordsMapper likeRecordsMapper,
                            SeriesPostsMapper seriesPostsMapper) {
        this.postsMapper = postsMapper;
        this.usersMapper = usersMapper;
        this.categoriesMapper = categoriesMapper;
        this.postTagsMapper = postTagsMapper;
        this.tagsMapper = tagsMapper;
        this.postContentsMapper = postContentsMapper;
        this.likeRecordsMapper = likeRecordsMapper;
        this.seriesPostsMapper = seriesPostsMapper;
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

        PostContents contents = postContentsMapper.selectByPostId(post.getId());
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
}
