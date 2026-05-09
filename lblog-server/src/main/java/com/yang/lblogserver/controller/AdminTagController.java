package com.yang.lblogserver.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Tags;
import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.TagsMapper;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.service.TagsService;
import com.yang.lblogserver.vo.TagVO;
import com.yang.lblogserver.vo.admin.AdminTagVO;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理端", description = "全站标签管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTagController {

    private final TagsService tagsService;
    private final TagsMapper tagsMapper;
    private final UsersMapper usersMapper;

    public AdminTagController(TagsService tagsService,
                              TagsMapper tagsMapper,
                              UsersMapper usersMapper) {
        this.tagsService = tagsService;
        this.tagsMapper = tagsMapper;
        this.usersMapper = usersMapper;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "全站标签列表", description = "分页查询全部标签，含创建者信息，支持按创建者筛选")
    @GetMapping("/tags")
    public ApiResponse<PageResult<AdminTagVO>> getAdminTags(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int pageSize,
            @RequestParam(required = false) Long createdBy) {
        PageHelper.startPage(page, pageSize);
        List<TagVO> list = tagsService.getTagList(pageSize, createdBy);
        PageInfo<TagVO> info = new PageInfo<>(list);

        List<AdminTagVO> voList = buildAdminTagVOList(list);
        return ApiResponse.success(PageResult.of(page, pageSize, info.getTotal(), voList));
    }

    @Operation(summary = "创建标签")
    @PostMapping("/tags")
    public ApiResponse<IdResponse> createTag(@Valid @RequestBody CreateTagRequest request) {
        if (!tagsService.checkSlug(request.getSlug(), null)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        Long id = tagsService.createTag(request, getCurrentUserId());
        return ApiResponse.success(new IdResponse(id));
    }

    @Operation(summary = "更新标签", description = "管理员可更新任意标签，不检查所有权")
    @PutMapping("/tags/{id}")
    public ApiResponse<?> updateTag(@PathVariable Long id, @Valid @RequestBody CreateTagRequest request) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        if (request.getSlug() != null && !tagsService.checkSlug(request.getSlug(), id)) {
            return ApiResponse.error(400, "URL 别名已存在");
        }
        tagsService.updateTag(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除标签", description = "管理员可删除任意标签")
    @DeleteMapping("/tags/{id}")
    public ApiResponse<?> deleteTag(@PathVariable Long id) {
        Tags tag = tagsMapper.selectById(id);
        if (tag == null) return ApiResponse.error(404, "标签不存在");
        tagsService.deleteTag(id);
        return ApiResponse.success(null);
    }

    /**
     * 将 TagVO 列表转换为 AdminTagVO 列表，填充创建者信息
     */
    private List<AdminTagVO> buildAdminTagVOList(List<TagVO> tagVOs) {
        if (tagVOs == null || tagVOs.isEmpty()) {
            return new ArrayList<>();
        }

        // 提取标签 ID
        List<Long> ids = tagVOs.stream().map(TagVO::getId).collect(Collectors.toList());

        // 批量查询 Tags 获取 createdBy
        List<Tags> tags = tagsMapper.selectBatchIds(ids);
        Map<Long, Long> tagIdToCreatedBy = tags.stream()
                .collect(Collectors.toMap(Tags::getId, Tags::getCreatedBy, (a, b) -> a));

        // 收集所有 creator ID
        List<Long> creatorIds = tags.stream()
                .map(Tags::getCreatedBy)
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

        // 组装 AdminTagVO
        List<AdminTagVO> result = new ArrayList<>(tagVOs.size());
        for (TagVO vo : tagVOs) {
            AdminTagVO adminVO = new AdminTagVO();
            adminVO.setId(vo.getId());
            adminVO.setName(vo.getName());
            adminVO.setSlug(vo.getSlug());
            adminVO.setPostCount(vo.getPostCount());

            Long creatorId = tagIdToCreatedBy.get(vo.getId());
            adminVO.setCreatedBy(creatorId);
            adminVO.setCreatorName(creatorId != null ? userIdToName.get(creatorId) : null);

            result.add(adminVO);
        }
        return result;
    }
}
