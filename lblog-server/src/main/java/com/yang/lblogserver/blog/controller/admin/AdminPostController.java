package com.yang.lblogserver.blog.controller.admin;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.blog.domain.Posts;
import com.yang.lblogserver.blog.mapper.PostsMapper;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.blog.service.CategoriesCacheService;
import com.yang.lblogserver.blog.service.HotPostsCacheService;
import com.yang.lblogserver.blog.service.PostsService;
import com.yang.lblogserver.blog.service.SeriesCacheService;
import com.yang.lblogserver.blog.service.TagsCacheService;
import com.yang.lblogserver.blog.vo.PostVO;
import com.yang.lblogserver.blog.vo.admin.BatchPostsRequest;
import com.yang.lblogserver.blog.vo.admin.UpdatePostRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理端", description = "全站文章管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPostController {

    private final PostsService postsService;
    private final PostsMapper postsMapper;
    private final CategoriesCacheService categoriesCacheService;
    private final TagsCacheService tagsCacheService;
    private final SeriesCacheService seriesCacheService;
    private final HotPostsCacheService hotPostsCacheService;

    public AdminPostController(PostsService postsService, PostsMapper postsMapper,
                               CategoriesCacheService categoriesCacheService,
                               TagsCacheService tagsCacheService,
                               SeriesCacheService seriesCacheService,
                               HotPostsCacheService hotPostsCacheService) {
        this.postsService = postsService;
        this.postsMapper = postsMapper;
        this.categoriesCacheService = categoriesCacheService;
        this.tagsCacheService = tagsCacheService;
        this.seriesCacheService = seriesCacheService;
        this.hotPostsCacheService = hotPostsCacheService;
    }

    private void refreshAllCaches() {
        categoriesCacheService.refresh();
        tagsCacheService.refresh();
        seriesCacheService.refresh();
        hotPostsCacheService.refresh();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "全站文章列表", description = "分页查询全站所有文章，支持按状态、关键词、作者筛选")
    @GetMapping("/posts")
    public ApiResponse<PageResult<PostVO>> getAdminPostList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long authorId) {
        return ApiResponse.success(postsService.getAdminPostList(page, pageSize, status, keyword, authorId));
    }

    @Operation(summary = "编辑文章元数据", description = "管理员可修改任意文章的标题、别名、状态、分类、标签等元数据，不涉及正文")
    @PutMapping("/posts/{id}")
    public ApiResponse<?> updatePost(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        Posts post = postsMapper.selectByIdRaw(id);
        if (post == null) return ApiResponse.error(404, "文章不存在");
        if (request.getSlug() != null && !postsService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        postsService.updatePost(id, request);
        refreshAllCaches();
        return ApiResponse.success(null);
    }

    @Operation(summary = "修改文章状态", description = "管理员可直接修改任意文章的状态")
    @PutMapping("/posts/{id}/status")
    public ApiResponse<?> updatePostStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return ApiResponse.error(400, "状态值无效，仅支持 0（草稿）或 1（已发布）");
        }
        Posts post = postsMapper.selectByIdRaw(id);
        if (post == null) {
            return ApiResponse.error(404, "文章不存在");
        }
        Posts update = new Posts();
        update.setId(id);
        update.setStatus(status);
        postsMapper.updatePost(update);
        refreshAllCaches();
        return ApiResponse.success(null);
    }

    @Operation(summary = "批量操作文章", description = "批量发布、转为草稿或软删除文章")
    @PostMapping("/posts/batch")
    public ApiResponse<?> batchPosts(@Valid @RequestBody BatchPostsRequest request) {
        String action = request.getAction();
        if (!List.of("PUBLISH", "DRAFT", "DELETE").contains(action)) {
            return ApiResponse.error(400, "操作类型无效，仅支持 PUBLISH / DRAFT / DELETE");
        }

        List<Long> failedIds = new ArrayList<>();
        int successCount = 0;

        for (Long id : request.getIds()) {
            try {
                Posts existing = postsMapper.selectByIdRaw(id);
                if (existing == null) {
                    failedIds.add(id);
                    continue;
                }
                switch (action) {
                    case "PUBLISH":
                    case "DRAFT": {
                        Posts update = new Posts();
                        update.setId(id);
                        update.setStatus("PUBLISH".equals(action) ? 1 : 0);
                        postsMapper.updatePost(update);
                        break;
                    }
                    case "DELETE":
                        postsService.deletePost(id);
                        break;
                }
                successCount++;
            } catch (Exception e) {
                failedIds.add(id);
            }
        }

        refreshAllCaches();

        if (failedIds.isEmpty()) {
            return ApiResponse.success(null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failedIds", failedIds);
        return ApiResponse.success(result);
    }
}
