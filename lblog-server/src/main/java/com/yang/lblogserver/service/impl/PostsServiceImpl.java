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

    public PostsServiceImpl(PostsMapper postsMapper, UsersMapper usersMapper,
                            CategoriesMapper categoriesMapper, PostTagsMapper postTagsMapper,
                            TagsMapper tagsMapper) {
        this.postsMapper = postsMapper;
        this.usersMapper = usersMapper;
        this.categoriesMapper = categoriesMapper;
        this.postTagsMapper = postTagsMapper;
        this.tagsMapper = tagsMapper;
    }

    @Override
    public PageResult<PostVO> getPostList(int page, int pageSize, String sort,
                                          Long categoryId, Long tagId, Long seriesId, String keyword) {
        // 校验sort参数，非法值回退到recommend
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
            PostVO vo = new PostVO();
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

            voList.add(vo);
        }

        return PageResult.of(page, pageSize, pageInfo.getTotal(), voList);
    }

    @Override
    public List<HotPostVO> getHotPosts(int limit) {
        return postsMapper.selectHotPosts(limit);
    }
}
