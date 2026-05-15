package com.yang.lblogserver.blog.controller.author;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.blog.domain.Categories;
import com.yang.lblogserver.blog.mapper.CategoriesMapper;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.blog.service.CategoriesService;
import com.yang.lblogserver.blog.vo.CategoryVO;
import com.yang.lblogserver.blog.vo.admin.CreateCategoryRequest;
import com.yang.lblogserver.auth.vo.IdResponse;
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

@Tag(name = "创作中心", description = "分类管理")
@Validated
@RestController
@RequestMapping("/api/v1/author")
@PreAuthorize("hasRole('AUTHOR')")
public class AuthorCategoryController {

    private final CategoriesService categoriesService;
    private final CategoriesMapper categoriesMapper;

    public AuthorCategoryController(CategoriesService categoriesService,
                                    CategoriesMapper categoriesMapper) {
        this.categoriesService = categoriesService;
        this.categoriesMapper = categoriesMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "获取分类列表（创作中心用）", description = "返回全部分类含文章数量")
    @GetMapping("/categories")
    public ApiResponse<PageResult<CategoryVO>> getAuthorCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<CategoryVO> list = categoriesService.getCategoryList(pageSize, getCurrentUserId());
        PageInfo<CategoryVO> info = new PageInfo<>(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), list));
    }

    @Operation(summary = "创建分类")
    @PostMapping("/categories")
    public ApiResponse<IdResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        Long id = categoriesService.createCategory(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新分类")
    @PutMapping("/categories/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest request) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (!getCurrentUserId().equals(cat.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的分类");
        }
        categoriesService.updateCategory(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除分类", description = "若分类下存在文章则无法删除")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (!getCurrentUserId().equals(cat.getCreatedBy())) {
            return ApiResponse.error(403, "只能操作自己创建的分类");
        }
        if (categoriesMapper.countPostsByCategoryId(id) > 0) {
            return ApiResponse.error(400, "该分类下存在文章，无法删除");
        }
        categoriesService.deleteCategory(id);
        return ApiResponse.success(null);
    }
}
