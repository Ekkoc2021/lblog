package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.CommentsService;
import com.yang.lblogserver.vo.CommentVO;
import com.yang.lblogserver.vo.CreateCommentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "评论", description = "文章评论（仅登录用户可发表）")
@Validated
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentsService commentsService;

    public CommentController(CommentsService commentsService) {
        this.commentsService = commentsService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        return null;
    }

    @Operation(summary = "获取顶级评论", description = "分页。已登录时同时返回自己的评论（不论审核状态）")
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResult<CommentVO>> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int pageSize,
            @RequestParam(defaultValue = "newest") String sort) {
        return ApiResponse.success(commentsService.getPostComments(postId, page, pageSize, sort, getCurrentUserId()));
    }

    @Operation(summary = "获取子回复", description = "分页加载某条顶级评论下的回复")
    @GetMapping("/posts/{postId}/comments/{rootId}/replies")
    public ApiResponse<PageResult<CommentVO>> getReplies(
            @PathVariable Long postId,
            @PathVariable Long rootId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) int pageSize) {
        return ApiResponse.success(commentsService.getReplies(rootId, page, pageSize, getCurrentUserId()));
    }

    @Operation(summary = "发表评论", description = "需登录")
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<Map<String, Long>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest servletRequest) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return ApiResponse.error(401, "请先登录");
        }

        String ip = servletRequest.getRemoteAddr();
        Long id = commentsService.createComment(
                postId, request.getContent(), request.getParentId(),
                loginUser.getUserId(), loginUser.getNickname(), loginUser.getAvatar(), ip);
        return ApiResponse.success(Map.of("id", id));
    }
}
