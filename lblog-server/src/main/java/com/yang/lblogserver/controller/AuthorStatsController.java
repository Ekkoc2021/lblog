package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.PostsService;
import com.yang.lblogserver.vo.admin.AuthorStatisticsVO;
import com.yang.lblogserver.vo.admin.StatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "创作中心", description = "数据统计")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorStatsController {

    private final PostsService postsService;

    public AuthorStatsController(PostsService postsService) {
        this.postsService = postsService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "获取站点统计数据")
    @GetMapping("/site-statistics")
    public ApiResponse<StatisticsVO> getStatistics() {
        StatisticsVO stats = postsService.getStatistics();
        return ApiResponse.success(stats);
    }

    @Operation(summary = "获取作者个人统计数据", description = "文章数、浏览量、点赞数、评论数、状态分布、分类分布、月度发文趋势")
    @GetMapping("/statistics")
    public ApiResponse<AuthorStatisticsVO> getAuthorStatistics() {
        Long userId = getCurrentUserId();
        AuthorStatisticsVO stats = postsService.getAuthorStatistics(userId);
        return ApiResponse.success(stats);
    }
}
