package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.mapper.*;
import com.yang.lblogserver.service.*;
import com.yang.lblogserver.vo.*;
import com.yang.lblogserver.vo.admin.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理端", description = "文章/分类/标签/专栏管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final PostsService postsService;
    private final CategoriesService categoriesService;
    private final TagsService tagsService;
    private final SeriesService seriesService;
    private final CategoriesMapper categoriesMapper;

    // TODO: 临时硬编码用户ID=1（admin），后续接入 JWT 后从 Token 解析
    private static final Long TEST_USER_ID = 1L;

    public AdminController(PostsService postsService, CategoriesService categoriesService,
                           TagsService tagsService, SeriesService seriesService,
                           CategoriesMapper categoriesMapper) {
        this.postsService = postsService;
        this.categoriesService = categoriesService;
        this.tagsService = tagsService;
        this.seriesService = seriesService;
        this.categoriesMapper = categoriesMapper;
    }

    // ==================== 文章管理 ====================

    @Operation(summary = "管理端文章列表", description = "含草稿/私密文章，分页返回")
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> getAdminPostList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(postsService.getAdminPostList(page, pageSize, status, keyword));
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
        return ApiResponse.success(vo);
    }

    @Operation(summary = "创建文章")
    @PostMapping("/posts")
    public ApiResponse<IdResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        // 检查 slug 是否已存在
        if (!postsService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = postsService.createPost(request, TEST_USER_ID);
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新文章")
    @PutMapping("/posts/{id}")
    public ApiResponse<?> updatePost(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        // 检查 slug 是否已存在（如果传了 slug）
        if (request.getSlug() != null && !postsService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        try {
            postsService.updatePost(id, request);
        } catch (RuntimeException e) {
            return ApiResponse.error(404, e.getMessage());
        }
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除文章（软删除）")
    @DeleteMapping("/posts/{id}")
    public ApiResponse<?> deletePost(@PathVariable Long id) {
        postsService.deletePost(id);
        return ApiResponse.success(null);
    }

    // ==================== 分类管理 ====================

    @Operation(summary = "创建分类")
    @PostMapping("/categories")
    public ApiResponse<IdResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Long id = categoriesService.createCategory(request, TEST_USER_ID);
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest request) {
        categoriesService.updateCategory(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除分类", description = "若分类下存在文章则无法删除")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        if (categoriesMapper.countPostsByCategoryId(id) > 0) {
            return ApiResponse.error(400, "该分类下存在文章，无法删除");
        }
        categoriesService.deleteCategory(id);
        return ApiResponse.success(null);
    }

    // ==================== 标签管理 ====================

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<IdResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        Long id = tagsService.createTag(request, TEST_USER_ID);
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/tags/{id}")
    public ApiResponse<?> updateTag(@PathVariable Long id, @RequestBody CreateTagRequest request) {
        tagsService.updateTag(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<?> deleteTag(@PathVariable Long id) {
        tagsService.deleteTag(id);
        return ApiResponse.success(null);
    }

    // ==================== 专栏管理 ====================

    @Operation(summary = "创建专栏")
    @PostMapping("/series")
    public ApiResponse<IdResponse> createSeries(@Valid @RequestBody CreateSeriesRequest request) {
        if (!seriesService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = seriesService.createSeries(request, TEST_USER_ID);
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新专栏")
    @PutMapping("/series/{id}")
    public ApiResponse<?> updateSeries(@PathVariable Long id, @RequestBody CreateSeriesRequest request) {
        if (request.getSlug() != null && !seriesService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        seriesService.updateSeries(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除专栏")
    @DeleteMapping("/series/{id}")
    public ApiResponse<?> deleteSeries(@PathVariable Long id) {
        seriesService.deleteSeries(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "关联文章到专栏", description = "全量替换，按数组顺序设置 sort_order")
    @PostMapping("/series/{seriesId}/posts")
    public ApiResponse<?> linkSeriesPosts(@PathVariable Long seriesId,
                                           @Valid @RequestBody PostIdsRequest request) {
        seriesService.linkPosts(seriesId, request.getPostIds());
        return ApiResponse.success(null);
    }

    @Operation(summary = "调整专栏文章排序")
    @PutMapping("/series/{seriesId}/posts/sort")
    public ApiResponse<?> reorderSeriesPosts(@PathVariable Long seriesId,
                                              @Valid @RequestBody PostIdsRequest request) {
        seriesService.reorderPosts(seriesId, request.getPostIds());
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
