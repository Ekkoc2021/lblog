package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.service.CommentsService;
import com.yang.lblogserver.vo.CommentStatusRequest;
import com.yang.lblogserver.vo.admin.AdminCommentVO;
import com.yang.lblogserver.vo.admin.BatchCommentsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理端", description = "全站评论审核")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommentController {

    private final CommentsService commentsService;

    public AdminCommentController(CommentsService commentsService) {
        this.commentsService = commentsService;
    }

    @Operation(summary = "全站评论列表", description = "分页查询全站所有评论，支持按状态、关键词、文章筛选")
    @GetMapping("/comments")
    public ApiResponse<PageResult<AdminCommentVO>> getAdminComments(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long postId) {
        return ApiResponse.success(commentsService.getAdminCommentPage(page, pageSize, status, keyword, postId));
    }

    @Operation(summary = "审核评论", description = "1=通过 2=驳回")
    @PutMapping("/comments/{id}/status")
    public ApiResponse<?> reviewComment(@PathVariable Long id, @Valid @RequestBody CommentStatusRequest request) {
        commentsService.updateStatus(id, request.getStatus());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public ApiResponse<?> deleteComment(@PathVariable Long id) {
        commentsService.deleteComment(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "批量操作评论", description = "批量通过、驳回或删除评论")
    @PostMapping("/comments/batch")
    public ApiResponse<?> batchComments(@Valid @RequestBody BatchCommentsRequest request) {
        String action = request.getAction();
        if (!List.of("APPROVE", "REJECT", "DELETE").contains(action)) {
            return ApiResponse.error(400, "操作类型无效，仅支持 APPROVE / REJECT / DELETE");
        }

        List<Long> failedIds = new ArrayList<>();
        int successCount = 0;

        for (Long id : request.getIds()) {
            try {
                switch (action) {
                    case "APPROVE":
                        commentsService.updateStatus(id, 1);
                        break;
                    case "REJECT":
                        commentsService.updateStatus(id, 2);
                        break;
                    case "DELETE":
                        commentsService.deleteComment(id);
                        break;
                }
                successCount++;
            } catch (Exception e) {
                failedIds.add(id);
            }
        }

        if (failedIds.isEmpty()) {
            return ApiResponse.success(null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failedIds", failedIds);
        return ApiResponse.success(result);
    }
}
