package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.service.CommentsService;
import com.yang.lblogserver.vo.CommentVO;
import com.yang.lblogserver.vo.CommentStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "创作中心", description = "评论管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorCommentController {

    private final CommentsService commentsService;

    public AuthorCommentController(CommentsService commentsService) {
        this.commentsService = commentsService;
    }

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
}
