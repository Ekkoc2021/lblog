package com.yang.lblogserver.draw.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.draw.service.UserDiagramsService;
import com.yang.lblogserver.draw.vo.DiagramDetailVO;
import com.yang.lblogserver.draw.vo.DiagramListVO;
import com.yang.lblogserver.draw.vo.SaveDiagramRequest;
import com.yang.lblogserver.draw.vo.UpdateMetaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户图表", description = "用户绘图存储管理")
@Validated
@RestController
@RequestMapping("/api/v1/diagrams")
public class UserDiagramController {

    private final UserDiagramsService userDiagramsService;

    public UserDiagramController(UserDiagramsService userDiagramsService) {
        this.userDiagramsService = userDiagramsService;
    }

    private Long getUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getUserId();
        }
        return null;
    }

    @Operation(summary = "新建图表")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Long>> create(@RequestBody SaveDiagramRequest request,
                                                  Authentication auth) {
        Long userId = getUserId(auth);
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ApiResponse.error(400, "标题不能为空");
        }
        if (request.getXmlData() == null || request.getXmlData().isBlank()) {
            return ApiResponse.error(400, "XML 内容不能为空");
        }
        Long id = userDiagramsService.create(userId, request);
        return ApiResponse.success(Map.of("id", id));
    }

    @Operation(summary = "获取图表列表")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResult<DiagramListVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(required = false) String keyword,
            Authentication auth) {
        Long userId = getUserId(auth);
        return ApiResponse.success(userDiagramsService.list(userId, page, pageSize, keyword));
    }

    @Operation(summary = "获取图表详情")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DiagramDetailVO> getById(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        DiagramDetailVO vo = userDiagramsService.getById(id, userId);
        if (vo == null) {
            return ApiResponse.error(404, "图表不存在");
        }
        return ApiResponse.success(vo);
    }

    @Operation(summary = "覆盖保存图表")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateContent(@PathVariable Long id,
                                            @RequestBody SaveDiagramRequest request,
                                            Authentication auth) {
        Long userId = getUserId(auth);
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return ApiResponse.error(400, "标题不能为空");
        }
        if (request.getXmlData() == null || request.getXmlData().isBlank()) {
            return ApiResponse.error(400, "XML 内容不能为空");
        }
        userDiagramsService.updateContent(id, userId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "更新图表元数据")
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> updateMeta(@PathVariable Long id,
                                         @RequestBody UpdateMetaRequest request,
                                         Authentication auth) {
        Long userId = getUserId(auth);
        userDiagramsService.updateMeta(id, userId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除图表")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        Long userId = getUserId(auth);
        userDiagramsService.delete(id, userId);
        return ApiResponse.success(null);
    }
}
