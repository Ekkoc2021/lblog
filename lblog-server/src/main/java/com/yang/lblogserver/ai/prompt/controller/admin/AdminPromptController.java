package com.yang.lblogserver.ai.prompt.controller.admin;

import com.yang.lblogserver.ai.prompt.domain.AiPrompt;
import com.yang.lblogserver.ai.prompt.domain.AiPromptAudit;
import com.yang.lblogserver.ai.prompt.mapper.AiPromptAuditMapper;
import com.yang.lblogserver.ai.prompt.mapper.AiPromptMapper;
import com.yang.lblogserver.ai.prompt.service.AiPromptService;
import com.yang.lblogserver.ai.prompt.vo.PromptUpdateRequest;
import com.yang.lblogserver.ai.prompt.vo.PromptVO;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端", description = "AI 提示词管理")
@RestController
@RequestMapping("/api/v1/admin/ai/prompts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromptController {

    private final AiPromptService promptService;
    private final AiPromptMapper promptMapper;
    private final AiPromptAuditMapper auditMapper;

    public AdminPromptController(AiPromptService promptService, AiPromptMapper promptMapper,
                                 AiPromptAuditMapper auditMapper) {
        this.promptService = promptService;
        this.promptMapper = promptMapper;
        this.auditMapper = auditMapper;
    }

    @Operation(summary = "列出提示词", description = "按模块筛选，显示当前生效版本。不传 module 则查全部")
    @GetMapping
    public ApiResponse<List<PromptVO>> list(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String promptKey,
            @RequestParam(required = false) Boolean isActive) {

        List<AiPrompt> list = promptMapper.selectList(module, promptKey, isActive);
        List<PromptVO> vos = list.stream().map(PromptVO::from).toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "提示词详情")
    @GetMapping("/{id}")
    public ApiResponse<PromptVO> getById(@PathVariable Long id) {
        AiPrompt prompt = promptService.getPromptById(id);
        if (prompt == null) {
            return ApiResponse.error(404, "Prompt not found");
        }
        return ApiResponse.success(PromptVO.from(prompt));
    }

    @Operation(summary = "历史版本列表")
    @GetMapping("/{id}/versions")
    public ApiResponse<List<PromptVO>> getVersions(@PathVariable Long id) {
        AiPrompt current = promptService.getPromptById(id);
        if (current == null) {
            return ApiResponse.error(404, "Prompt not found");
        }
        List<AiPrompt> versions = promptMapper.selectVersions(current.getModule(), current.getPromptKey());
        List<PromptVO> vos = versions.stream().map(PromptVO::from).toList();
        return ApiResponse.success(vos);
    }

    @Operation(summary = "新增提示词")
    @PostMapping
    public ApiResponse<PromptVO> create(@RequestBody AiPrompt prompt) {
        AiPrompt created = promptService.createPrompt(prompt);
        return ApiResponse.success(PromptVO.from(created));
    }

    @Operation(summary = "更新提示词内容", description = "INSERT 新版本，旧版失效")
    @PutMapping("/{id}")
    public ApiResponse<PromptVO> update(@PathVariable Long id, @RequestBody PromptUpdateRequest request) {
        AiPrompt updated = promptService.updatePrompt(id, request.getContent(), request.getOperator());
        return ApiResponse.success(PromptVO.from(updated));
    }

    @Operation(summary = "更新元数据", description = "更新描述或排序值，不修改内容")
    @PatchMapping("/{id}")
    public ApiResponse<PromptVO> updateMeta(@PathVariable Long id, @RequestBody PromptUpdateRequest request) {
        AiPrompt updated = promptService.updatePromptMeta(id, request.getDescription(), request.getSortOrder(), request.getOperator());
        return ApiResponse.success(PromptVO.from(updated));
    }

    @Operation(summary = "删除提示词", description = "软删除，设 is_active=0")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id, @RequestParam(defaultValue = "admin") String operator) {
        promptService.deactivatePrompt(id, operator);
        return ApiResponse.success(null);
    }

    @Operation(summary = "清除缓存", description = "重新从 DB 加载提示词")
    @PostMapping("/reload")
    public ApiResponse<?> reload() {
        promptService.reloadCache();
        return ApiResponse.success(null);
    }

    @Operation(summary = "导入文件到 DB", description = "将 classpath:prompts/{module}/ 下的文件导入 ai_prompts 表，已存在的不覆盖")
    @PostMapping("/seed")
    public ApiResponse<?> seed(@RequestParam String module) {
        int count = promptService.seedFromFiles(module);
        return ApiResponse.success("Seeded " + count + " prompts for module: " + module);
    }

    @Operation(summary = "审计日志")
    @GetMapping("/{id}/audit")
    public ApiResponse<List<AiPromptAudit>> getAudit(@PathVariable Long id) {
        List<AiPromptAudit> list = auditMapper.selectByPromptId(id);
        return ApiResponse.success(list);
    }
}
