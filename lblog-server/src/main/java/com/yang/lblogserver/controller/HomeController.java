package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.service.*;
import com.yang.lblogserver.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "首页", description = "博客首页相关接口")
@Validated
@RestController
@RequestMapping("/api/v1")
public class HomeController {

    private final PostsService postsService;
    private final CategoriesService categoriesService;
    private final TagsService tagsService;
    private final SeriesService seriesService;

    public HomeController(PostsService postsService, CategoriesService categoriesService,
                          TagsService tagsService, SeriesService seriesService) {
        this.postsService = postsService;
        this.categoriesService = categoriesService;
        this.tagsService = tagsService;
        this.seriesService = seriesService;
    }

    @Operation(summary = "获取文章列表", description = "首页核心接口，支持分页、排序、分类/标签/专栏筛选和关键词搜索")
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> getPosts(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "排序方式: recommend(推荐)/newest(最新)/hot(最热)") @RequestParam(defaultValue = "recommend") String sort,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "标签ID") @RequestParam(required = false) Long tagId,
            @Parameter(description = "专栏ID") @RequestParam(required = false) Long seriesId,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword) {
        PageResult<PostVO> result = postsService.getPostList(page, pageSize, sort, categoryId, tagId, seriesId, keyword);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取分类列表", description = "导航栏及筛选使用，只返回顶级分类，含文章数量")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> getCategories() {
        return ApiResponse.success(categoriesService.getCategoryList());
    }

    @Operation(summary = "获取标签列表", description = "按文章数量降序返回热门标签")
    @GetMapping("/tags")
    public ApiResponse<List<TagVO>> getTags(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(tagsService.getTagList(limit));
    }

    @Operation(summary = "获取专栏列表", description = "侧边栏专栏推荐展示，含文章数量")
    @GetMapping("/series")
    public ApiResponse<List<SeriesVO>> getSeries(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") @Min(1) @Max(100) int limit,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(seriesService.getSeriesList(limit, categoryId));
    }

    @Operation(summary = "获取热门文章", description = "按浏览量降序返回热门文章排行")
    @GetMapping("/posts/hot")
    public ApiResponse<List<HotPostVO>> getHotPosts(
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "5") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(postsService.getHotPosts(limit));
    }
}
