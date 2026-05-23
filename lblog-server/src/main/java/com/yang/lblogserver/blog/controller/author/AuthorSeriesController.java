package com.yang.lblogserver.blog.controller.author;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.blog.domain.Series;
import com.yang.lblogserver.blog.mapper.SeriesMapper;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.blog.service.SeriesService;
import com.yang.lblogserver.blog.vo.SeriesPostVO;
import com.yang.lblogserver.blog.vo.SeriesVO;
import com.yang.lblogserver.blog.vo.admin.CreateSeriesRequest;
import com.yang.lblogserver.auth.vo.IdResponse;
import com.yang.lblogserver.blog.vo.admin.PostIdsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "创作中心", description = "专栏管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorSeriesController {

    private final SeriesService seriesService;
    private final SeriesMapper seriesMapper;

    public AuthorSeriesController(SeriesService seriesService, SeriesMapper seriesMapper) {
        this.seriesService = seriesService;
        this.seriesMapper = seriesMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

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

    @Operation(summary = "获取专栏文章列表")
    @GetMapping("/series/{seriesId}/posts")
    public ApiResponse<List<SeriesPostVO>> getSeriesPosts(@PathVariable Long seriesId) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        return ApiResponse.success(seriesService.getPostsBySeriesId(seriesId));
    }

    @Operation(summary = "从专栏移除文章")
    @DeleteMapping("/series/{seriesId}/posts/{postId}")
    public ApiResponse<?> removeSeriesPost(@PathVariable Long seriesId, @PathVariable Long postId) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (!getCurrentUserId().equals(series.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的专栏");
        }
        if (!seriesService.removePostFromSeries(seriesId, postId)) {
            return ApiResponse.error(404, "文章不在该专栏中");
        }
        return ApiResponse.success(null);
    }
}
