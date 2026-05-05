package com.yang.lblogserver.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.*;
import com.yang.lblogserver.mapper.*;
import com.yang.lblogserver.security.model.LoginUser;

import java.util.List;
import com.yang.lblogserver.service.*;
import com.yang.lblogserver.vo.*;
import com.yang.lblogserver.vo.admin.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端", description = "文章/分类/标签/专栏管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
public class AdminController {

    private final PostsService postsService;
    private final CategoriesService categoriesService;
    private final TagsService tagsService;
    private final SeriesService seriesService;
    private final CommentsService commentsService;
    private final CategoriesMapper categoriesMapper;
    private final TagsMapper tagsMapper;
    private final SeriesMapper seriesMapper;

    public AdminController(PostsService postsService, CategoriesService categoriesService,
                           TagsService tagsService, SeriesService seriesService,
                           CommentsService commentsService,
                           CategoriesMapper categoriesMapper,
                           TagsMapper tagsMapper, SeriesMapper seriesMapper) {
        this.postsService = postsService;
        this.categoriesService = categoriesService;
        this.tagsService = tagsService;
        this.seriesService = seriesService;
        this.commentsService = commentsService;
        this.categoriesMapper = categoriesMapper;
        this.tagsMapper = tagsMapper;
        this.seriesMapper = seriesMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    // ==================== 文章管理 ====================

    @Operation(summary = "管理端文章列表", description = "含草稿/私密文章，分页返回")
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> getAdminPostList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        // 只能看到自己的文章
        Long authorId = getCurrentUserId();
        return ApiResponse.success(postsService.getAdminPostList(page, pageSize, status, keyword, authorId));
    }

    @Operation(summary = "校验 Slug 可用性")
    @GetMapping("/posts/check-slug")
    public ApiResponse<SlugCheckResponse> checkSlug(
            @RequestParam String slug,
            @RequestParam(required = false) Long excludeId) {
        boolean available = postsService.checkSlug(slug, excludeId);
        return ApiResponse.success(new SlugCheckResponse(available));
    }

    @Operation(summary = "获取单篇文章（编辑用）", description = "含正文，不过滤 status")
    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetailVO> getAdminPostById(@PathVariable Long id) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        return ApiResponse.success(vo);
    }

    @Operation(summary = "创建文章")
    @PostMapping("/posts")
    public ApiResponse<IdResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        // 检查 slug 是否已存在
        if (!postsService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = postsService.createPost(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/posts/{id}")
    public ApiResponse<?> updatePost(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        // 检查 slug 是否已存在（如果传了 slug）
        if (request.getSlug() != null && !postsService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        postsService.updatePost(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文章（软删除）")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<?> deletePost(@PathVariable Long id) {
        PostDetailVO vo = postsService.getAdminPostById(id);
        if (vo == null) return ApiResponse.error(404, "文章不存在");
        if (!vo.getAuthorId().equals(getCurrentUserId())) {
            return ApiResponse.error(403, "只能操作自己的文章");
        }
        postsService.deletePost(id);
        return ApiResponse.success(null);
    }

    // ==================== 分类管理 ====================

    @Operation(summary = "获取分类列表（创作中心用）", description = "返回全部分类含文章数量")
    @GetMapping("/categories")
    public ApiResponse<PageResult<CategoryVO>> getAuthorCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<CategoryVO> list = categoriesService.getCategoryList(pageSize, getCurrentUserId());
        PageInfo<CategoryVO> info = new PageInfo<>(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), list));
    }

    @Operation(summary = "创建分类")
    @PostMapping("/categories")
    public ApiResponse<IdResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Long id = categoriesService.createCategory(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest request) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (!getCurrentUserId().equals(cat.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的分类");
        }
        categoriesService.updateCategory(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除分类", description = "若分类下存在文章则无法删除")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (!getCurrentUserId().equals(cat.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的分类");
        }
        if (categoriesMapper.countPostsByCategoryId(id) > 0) {
            return ApiResponse.error(400, "该分类下存在文章，无法删除");
        }
        categoriesService.deleteCategory(id);
        return ApiResponse.success(null);
    }

    // ==================== 标签管理 ====================

    @Operation(summary = "获取标签列表（创作中心用）", description = "返回全部标签含文章数量")
    @GetMapping("/tags")
    public ApiResponse<PageResult<TagVO>> getAuthorTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<TagVO> list = tagsService.getTagList(pageSize, getCurrentUserId());
        PageInfo<TagVO> info = new PageInfo<>(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), list));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<IdResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        Long id = tagsService.createTag(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/tags/{id}")
    public ApiResponse<?> updateTag(@PathVariable Long id, @RequestBody CreateTagRequest request) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        if (!getCurrentUserId().equals(tag.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的标签");
        }
        tagsService.updateTag(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<?> deleteTag(@PathVariable Long id) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        if (!getCurrentUserId().equals(tag.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的标签");
        }
        tagsService.deleteTag(id);
        return ApiResponse.success(null);
    }

    // ==================== 专栏管理 ====================

    @Operation(summary = "获取专栏列表（创作中心用）", description = "返回全部专栏含文章数量")
    @GetMapping("/series")
    public ApiResponse<PageResult<SeriesVO>> getAuthorSeries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize,
            @RequestParam(required = false) Long categoryId) {
        PageHelper.startPage(page, pageSize);
        List<SeriesVO> list = seriesService.getSeriesList(pageSize, categoryId, getCurrentUserId());
        PageInfo<SeriesVO> info = new PageInfo<>(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), list));
    }

    @Operation(summary = "创建专栏")
    @PostMapping("/series")
    public ApiResponse<IdResponse> createSeries(@Valid @RequestBody CreateSeriesRequest request) {
        if (!seriesService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = seriesService.createSeries(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新专栏")
    @PutMapping("/series/{id}")
    public ApiResponse<?> updateSeries(@PathVariable Long id, @RequestBody CreateSeriesRequest request) {
        Series series = seriesMapper.selectById(id);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        if (request.getSlug() != null && !seriesService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        seriesService.updateSeries(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除专栏")
    @DeleteMapping("/series/{id}")
    public ApiResponse<?> deleteSeries(@PathVariable Long id) {
        Series series = seriesMapper.selectById(id);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        seriesService.deleteSeries(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "关联文章到专栏", description = "全量替换，按数组顺序设置 sort_order")
    @PostMapping("/series/{seriesId}/posts")
    public ApiResponse<?> linkSeriesPosts(@PathVariable Long seriesId,
                                           @Valid @RequestBody PostIdsRequest request) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        seriesService.linkPosts(seriesId, request.getPostIds());
        return ApiResponse.success(null);
    }

    @Operation(summary = "调整专栏文章排序")
    @PutMapping("/series/{seriesId}/posts/sort")
    public ApiResponse<?> reorderSeriesPosts(@PathVariable Long seriesId,
                                              @Valid @RequestBody PostIdsRequest request) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        seriesService.reorderPosts(seriesId, request.getPostIds());
        return ApiResponse.success(null);
    }

    // ==================== 评论管理 ====================

    @Operation(summary = "评论列表", description = "按审核状态筛选")
    @GetMapping("/comments")
    public ApiResponse<List<CommentVO>> getCommentList(
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(commentsService.getAdminCommentList(status));
    }

    @Operation(summary = "审核评论", description = "1=通过 2=驳回")
    @PutMapping("/comments/{id}/status")
    public ApiResponse<?> reviewComment(@PathVariable Long id, @RequestBody CommentStatusRequest request) {
        commentsService.updateStatus(id, request.getStatus());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<?> deleteComment(@PathVariable Long id) {
        commentsService.deleteComment(id);
        return ApiResponse.success(null);
    }

    // ==================== 统计 ====================

    @Operation(summary = "获取站点统计数据")
    @GetMapping("/statistics")
    public ApiResponse<StatisticsVO> getStatistics() {
        StatisticsVO stats = postsService.getStatistics();
        return ApiResponse.success(stats);
    }
}
