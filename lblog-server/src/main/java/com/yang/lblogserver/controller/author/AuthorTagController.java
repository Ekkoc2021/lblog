package com.yang.lblogserver.controller.author;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Tags;
import com.yang.lblogserver.mapper.TagsMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.TagsService;
import com.yang.lblogserver.vo.response.TagVO;
import com.yang.lblogserver.vo.admin.CreateTagRequest;
import com.yang.lblogserver.vo.admin.IdResponse;
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

@Tag(name = "创作中心", description = "标签管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorTagController {

    private final TagsService tagsService;
    private final TagsMapper tagsMapper;

    public AuthorTagController(TagsService tagsService, TagsMapper tagsMapper) {
        this.tagsService = tagsService;
        this.tagsMapper = tagsMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "获取标签列表（创作中心用）", description = "返回全部标签含文章数量")
    @GetMapping("/tags")
    public ApiResponse<PageResult<TagVO>> getAuthorTags(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<TagVO> list = tagsService.getTagList(pageSize, getCurrentUserId());
        PageInfo<TagVO> info = new PageInfo<>(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), list));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<IdResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        Long id = tagsService.createTag(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新标签")
    @PutMapping("/tags/{id}")
    public ApiResponse<?> updateTag(@PathVariable Long id, @RequestBody CreateTagRequest request) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        if (!getCurrentUserId().equals(tag.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的标签");
        }
        tagsService.updateTag(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<?> deleteTag(@PathVariable Long id) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        if (!getCurrentUserId().equals(tag.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的标签");
        }
        tagsService.deleteTag(id);
        return ApiResponse.success(null);
    }
}
