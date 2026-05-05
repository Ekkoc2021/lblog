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
            @Parameter(description = "页码") @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页条数") @RequestParam(name = "pageSize", defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "排序方式: recommend(推荐)/newest(最新)/hot(最热)") @RequestParam(name = "sort", defaultValue = "recommend") String sort,
            @Parameter(description = "分类ID") @RequestParam(name = "categoryId", required = false) Long categoryId,
            @Parameter(description = "标签ID") @RequestParam(name = "tagId", required = false) Long tagId,
            @Parameter(description = "专栏ID") @RequestParam(name = "seriesId", required = false) Long seriesId,
            @Parameter(description = "搜索关键词") @RequestParam(name = "keyword", required = false) String keyword) {
        PageResult<PostVO> result = postsService.getPostList(page, pageSize, sort, categoryId, tagId, seriesId, keyword);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取分类列表", description = "按文章数量降序返回热门分类，含文章数量")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryVO>> getCategories(
            @Parameter(description = "返回数量") @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(100) int limit) {
        List<CategoryVO> catList = categoriesService.getCategoryList(limit, null);
        if (catList.size() > limit) catList = catList.subList(0, limit);
        return ApiResponse.success(catList);
    }

    @Operation(summary = "获取标签列表", description = "按文章数量降序返回热门标签")
    @GetMapping("/tags")
    public ApiResponse<List<TagVO>> getTags(
            @Parameter(description = "返回数量") @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit) {
        List<TagVO> tagList = tagsService.getTagList(limit, null);
        if (tagList.size() > limit) tagList = tagList.subList(0, limit);
        return ApiResponse.success(tagList);
    }

    @Operation(summary = "获取专栏列表", description = "侧边栏专栏推荐展示，含文章数量")
    @GetMapping("/series")
    public ApiResponse<List<SeriesVO>> getSeries(
            @Parameter(description = "返回数量") @RequestParam(name = "limit", defaultValue = "5") @Min(1) @Max(100) int limit,
            @Parameter(description = "分类ID") @RequestParam(name = "categoryId", required = false) Long categoryId) {
        List<SeriesVO> seriesList = seriesService.getSeriesList(limit, categoryId, null);
        if (seriesList.size() > limit) seriesList = seriesList.subList(0, limit);
        return ApiResponse.success(seriesList);
    }

    @Operation(summary = "获取热门文章", description = "按浏览量降序返回热门文章排行")
    @GetMapping("/posts/hot")
    public ApiResponse<List<HotPostVO>> getHotPosts(
            @Parameter(description = "返回数量") @RequestParam(name = "limit", defaultValue = "5") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(postsService.getHotPosts(limit));
    }

    @Operation(summary = "获取文章详情", description = "根据slug获取文章完整信息（含正文）")
    @GetMapping("/posts/{slug}")
    public ApiResponse<PostDetailVO> getPostBySlug(
            @Parameter(description = "文章URL标识") @PathVariable String slug) {
        PostDetailVO vo = postsService.getPostBySlug(slug);
        if (vo == null) {
            return ApiResponse.error(404, "文章不存在");
        }
        return ApiResponse.success(vo);
    }

    @Operation(summary = "浏览上报", description = "访问文章详情页时上报一次浏览，浏览量+1")
    @PostMapping("/posts/{id}/view")
    public ApiResponse<Void> reportView(
            @Parameter(description = "文章ID") @PathVariable Long id) {
        postsService.reportView(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "点赞", description = "为文章点赞，同一访客重复点赞幂等")
    @PostMapping("/posts/{id}/like")
    public ApiResponse<LikeResponseVO> likePost(
            @Parameter(description = "文章ID") @PathVariable Long id,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId == null || visitorId.isEmpty()) {
            return ApiResponse.error(400, "visitorId不能为空");
        }
        LikeResponseVO vo = postsService.likePost(id, visitorId);
        if (vo == null) {
            return ApiResponse.error(404, "文章不存在");
        }
        return ApiResponse.success(vo);
    }

    @Operation(summary = "取消点赞", description = "取消对文章的点赞，幂等")
    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<LikeResponseVO> unlikePost(
            @Parameter(description = "文章ID") @PathVariable Long id,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId == null || visitorId.isEmpty()) {
            return ApiResponse.error(400, "visitorId不能为空");
        }
        return ApiResponse.success(postsService.unlikePost(id, visitorId));
    }

    @Operation(summary = "查询点赞状态", description = "查询当前访客对文章的点赞状态")
    @GetMapping("/posts/{id}/like/status")
    public ApiResponse<LikeStatusVO> getLikeStatus(
            @Parameter(description = "文章ID") @PathVariable Long id,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        if (visitorId == null || visitorId.isEmpty()) {
            return ApiResponse.error(400, "visitorId不能为空");
        }
        return ApiResponse.success(postsService.getLikeStatus(id, visitorId));
    }
}
