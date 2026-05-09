package com.yang.lblogserver.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Series;
import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.SeriesMapper;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.SeriesService;
import com.yang.lblogserver.vo.SeriesVO;
import com.yang.lblogserver.vo.admin.AdminSeriesVO;
import com.yang.lblogserver.vo.admin.CreateSeriesRequest;
import com.yang.lblogserver.vo.admin.IdResponse;
import com.yang.lblogserver.vo.admin.PostIdsRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理端", description = "全站专栏管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeriesController {

    private final SeriesService seriesService;
    private final SeriesMapper seriesMapper;
    private final UsersMapper usersMapper;

    public AdminSeriesController(SeriesService seriesService,
                                 SeriesMapper seriesMapper,
                                 UsersMapper usersMapper) {
        this.seriesService = seriesService;
        this.seriesMapper = seriesMapper;
        this.usersMapper = usersMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "全站专栏列表", description = "分页查询全部专栏，含创建者信息，支持按创建者筛选")
    @GetMapping("/series")
    public ApiResponse<PageResult<AdminSeriesVO>> getAdminSeries(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long createdBy) {
        PageHelper.startPage(page, pageSize);
        List<SeriesVO> list = seriesService.getSeriesList(pageSize, categoryId, createdBy);
        PageInfo<SeriesVO> info = new PageInfo<>(list);

        List<AdminSeriesVO> voList = buildAdminSeriesVOList(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), voList));
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

    @Operation(summary = "更新专栏", description = "管理员可更新任意专栏，不检查所有权")
    @PutMapping("/series/{id}")
    public ApiResponse<?> updateSeries(@PathVariable Long id, @Valid @RequestBody CreateSeriesRequest request) {
        Series series = seriesMapper.selectById(id);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        if (request.getSlug() != null && !seriesService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        seriesService.updateSeries(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除专栏", description = "管理员可删除任意专栏")
    @DeleteMapping("/series/{id}")
    public ApiResponse<?> deleteSeries(@PathVariable Long id) {
        Series series = seriesMapper.selectById(id);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        seriesService.deleteSeries(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "关联文章到专栏", description = "全量替换，按数组顺序设置 sort_order")
    @PostMapping("/series/{seriesId}/posts")
    public ApiResponse<?> linkSeriesPosts(@PathVariable Long seriesId,
                                           @Valid @RequestBody PostIdsRequest request) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        seriesService.linkPosts(seriesId, request.getPostIds());
        return ApiResponse.success(null);
    }

    @Operation(summary = "调整专栏文章排序")
    @PutMapping("/series/{seriesId}/posts/sort")
    public ApiResponse<?> reorderSeriesPosts(@PathVariable Long seriesId,
                                              @Valid @RequestBody PostIdsRequest request) {
        Series series = seriesMapper.selectById(seriesId);
        if (series == null) return ApiResponse.error(404, "专栏不存在");
        seriesService.reorderPosts(seriesId, request.getPostIds());
        return ApiResponse.success(null);
    }

    /**
     * 将 SeriesVO 列表转换为 AdminSeriesVO 列表，填充创建者信息
     */
    private List<AdminSeriesVO> buildAdminSeriesVOList(List<SeriesVO> seriesVOs) {
        if (seriesVOs == null || seriesVOs.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取专栏 ID
        List<Long> ids = seriesVOs.stream().map(SeriesVO::getId).collect(Collectors.toList());

        // 批量查询 Series 获取 createdBy
        List<Series> seriesList = seriesMapper.selectBatchIds(ids);
        Map<Long, Long> seriesIdToCreatedBy = seriesList.stream()
                .collect(Collectors.toMap(Series::getId, Series::getCreatedBy, (a, b) -> a));

        // 收集所有 creator ID
        List<Long> creatorIds = seriesList.stream()
                .map(Series::getCreatedBy)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询用户昵称
        Map<Long, String> userIdToName;
        if (creatorIds.isEmpty()) {
            userIdToName = Map.of();
        } else {
            List<Users> users = usersMapper.selectBatchIds(creatorIds);
            userIdToName = users.stream()
                    .collect(Collectors.toMap(Users::getId, Users::getNickname, (a, b) -> a));
        }

        // 组装 AdminSeriesVO
        List<AdminSeriesVO> result = new ArrayList<>(seriesVOs.size());
        for (SeriesVO vo : seriesVOs) {
            AdminSeriesVO adminVO = new AdminSeriesVO();
            adminVO.setId(vo.getId());
            adminVO.setTitle(vo.getTitle());
            adminVO.setSlug(vo.getSlug());
            adminVO.setDescription(vo.getDescription());
            adminVO.setCoverImageUrl(vo.getCoverImageUrl());
            adminVO.setCategoryId(vo.getCategoryId());
            adminVO.setIsCompleted(vo.getIsCompleted());
            adminVO.setSortOrder(vo.getSortOrder());
            adminVO.setPostCount(vo.getPostCount());

            Long creatorId = seriesIdToCreatedBy.get(vo.getId());
            adminVO.setCreatedBy(creatorId);
            adminVO.setCreatorName(creatorId != null ? userIdToName.get(creatorId) : null);

            result.add(adminVO);
        }
        return result;
    }
}
