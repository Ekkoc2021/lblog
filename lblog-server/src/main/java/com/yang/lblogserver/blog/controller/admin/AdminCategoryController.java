package com.yang.lblogserver.blog.controller.admin;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.blog.domain.Categories;
import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.blog.mapper.CategoriesMapper;
import com.yang.lblogserver.auth.service.UserQueryService;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.blog.service.CategoriesCacheService;
import com.yang.lblogserver.blog.service.CategoriesService;
import com.yang.lblogserver.blog.vo.CategoryVO;
import com.yang.lblogserver.blog.vo.admin.AdminCategoryVO;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理端", description = "全站分类管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoriesService categoriesService;
    private final CategoriesMapper categoriesMapper;
    private final UserQueryService userQueryService;
    private final CategoriesCacheService categoriesCacheService;

    public AdminCategoryController(CategoriesService categoriesService,
                                   CategoriesMapper categoriesMapper,
                                   UserQueryService userQueryService,
                                   CategoriesCacheService categoriesCacheService) {
        this.categoriesService = categoriesService;
        this.categoriesMapper = categoriesMapper;
        this.userQueryService = userQueryService;
        this.categoriesCacheService = categoriesCacheService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "全站分类列表", description = "分页查询全部分类，含创建者信息，支持按创建者筛选")
    @GetMapping("/categories")
    public ApiResponse<PageResult<AdminCategoryVO>> getAdminCategories(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize,
            @RequestParam(required = false) Long createdBy) {
        PageHelper.startPage(page, pageSize);
        List<CategoryVO> list = categoriesService.getCategoryList(pageSize, createdBy);
        PageInfo<CategoryVO> info = new PageInfo<>(list);

        List<AdminCategoryVO> voList = buildAdminCategoryVOList(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), voList));
    }

    @Operation(summary = "创建分类")
    @PostMapping("/categories")
    public ApiResponse<IdResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        if (!categoriesService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = categoriesService.createCategory(request, getCurrentUserId());
        categoriesCacheService.refresh();
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新分类", description = "管理员可更新任意分类，不检查所有权")
    @PutMapping("/categories/{id}")
    public ApiResponse<?> updateCategory(@PathVariable Long id, @Valid @RequestBody CreateCategoryRequest request) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (request.getSlug() != null && !categoriesService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        categoriesService.updateCategory(id, request);
        categoriesCacheService.refresh();
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除分类", description = "管理员可删除任意分类，若分类下有文章则无法删除")
    @DeleteMapping("/categories/{id}")
    public ApiResponse<?> deleteCategory(@PathVariable Long id) {
        Categories cat = categoriesMapper.selectById(id);
        if (cat == null) return ApiResponse.error(404, "分类不存在");
        if (categoriesMapper.countPostsByCategoryId(id) > 0) {
            return ApiResponse.error(400, "该分类下有文章，无法删除");
        }
        categoriesService.deleteCategory(id);
        categoriesCacheService.refresh();
        return ApiResponse.success(null);
    }

    /**
     * 将 CategoryVO 列表转换为 AdminCategoryVO 列表，填充创建者信息
     */
    private List<AdminCategoryVO> buildAdminCategoryVOList(List<CategoryVO> catVOs) {
        if (catVOs == null || catVOs.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取分类 ID
        List<Long> ids = catVOs.stream().map(CategoryVO::getId).collect(Collectors.toList());

        // 批量查询 Categories 获取 createdBy
        List<Categories> categories = categoriesMapper.selectBatchIds(ids);
        Map<Long, Long> catIdToCreatedBy = categories.stream()
                .collect(Collectors.toMap(Categories::getId, Categories::getCreatedBy, (a, b) -> a));

        // 收集所有 creator ID
        List<Long> creatorIds = categories.stream()
                .map(Categories::getCreatedBy)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询用户昵称
        Map<Long, String> userIdToName;
        if (creatorIds.isEmpty()) {
            userIdToName = Map.of();
        } else {
            List<Users> users = userQueryService.selectBatchIds(creatorIds);
            userIdToName = users.stream()
                    .collect(Collectors.toMap(Users::getId, Users::getNickname, (a, b) -> a));
        }

        // 组装 AdminCategoryVO
        List<AdminCategoryVO> result = new ArrayList<>(catVOs.size());
        for (CategoryVO vo : catVOs) {
            AdminCategoryVO adminVO = new AdminCategoryVO();
            adminVO.setId(vo.getId());
            adminVO.setName(vo.getName());
            adminVO.setSlug(vo.getSlug());
            adminVO.setParentId(vo.getParentId());
            adminVO.setDescription(vo.getDescription());
            adminVO.setSortOrder(vo.getSortOrder());
            adminVO.setPostCount(vo.getPostCount());

            Long creatorId = catIdToCreatedBy.get(vo.getId());
            adminVO.setCreatedBy(creatorId);
            adminVO.setCreatorName(creatorId != null ? userIdToName.get(creatorId) : null);

            result.add(adminVO);
        }
        return result;
    }
}
