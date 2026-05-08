package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Images;
import com.yang.lblogserver.mapper.ImagesMapper;
import com.yang.lblogserver.storage.FileStorage;
import com.yang.lblogserver.vo.admin.AdminImageVO;
import com.yang.lblogserver.vo.admin.ImageCleanupVO;
import com.yang.lblogserver.vo.admin.ImageStatisticsVO;
import com.yang.lblogserver.vo.admin.ImageUsageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "管理端", description = "图片管理（管理端）")
@Validated
@RestController
@RequestMapping("/api/v1/admin/images")
@PreAuthorize("hasRole('ADMIN')")
public class AdminImageController {

    private final ImagesMapper imagesMapper;
    private final FileStorage fileStorage;

    public AdminImageController(ImagesMapper imagesMapper, FileStorage fileStorage) {
        this.imagesMapper = imagesMapper;
        this.fileStorage = fileStorage;
    }

    @Operation(summary = "图片统计概览", description = "总图片数、总大小、已引用/未引用数量、30天以上未引用等")
    @GetMapping("/statistics")
    public ApiResponse<ImageStatisticsVO> getStatistics() {
        ImageStatisticsVO stats = imagesMapper.selectImageStatistics();
        // 计算利用率
        if (stats.getTotalImages() > 0) {
            double rate = (double) stats.getReferencedCount() / stats.getTotalImages() * 100;
            // 保留两位小数
            stats.setUtilizationRate(Math.round(rate * 100.0) / 100.0);
        }
        return ApiResponse.success(stats);
    }

    @Operation(summary = "图片列表", description = "分页查询图片库，支持排序、状态筛选、关键词搜索")
    @GetMapping
    public ApiResponse<PageResult<AdminImageVO>> getImageList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        int offset = (page - 1) * pageSize;
        List<AdminImageVO> list = imagesMapper.selectAdminList(keyword, status, sort, offset, pageSize);
        int total = imagesMapper.countAdminList(keyword, status);

        // 对于有引用的图片，填充引用详情
        for (AdminImageVO vo : list) {
            if (vo.getUsageCount() > 0) {
                List<ImageUsageVO> usages = imagesMapper.selectImageUsages(vo.getId());
                vo.setUsages(usages);
            }
        }

        return ApiResponse.success(PageResult.of(page, pageSize, total, list));
    }

    @Operation(summary = "清理未引用图片",
            description = "dryRun=true（默认）仅预览；dryRun=false 执行实际物理删除（删磁盘文件 + 硬删 DB 记录）")
    @DeleteMapping("/cleanup")
    public ApiResponse<ImageCleanupVO> cleanupImages(
            @RequestParam(defaultValue = "30") int beforeDays,
            @RequestParam(required = false) Double targetUtilization,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        // 1. 统计当前利用率
        ImageStatisticsVO stats = imagesMapper.selectImageStatistics();
        long totalImages = stats.getTotalImages();
        long referencedCount = stats.getReferencedCount();
        double currentUtil = totalImages > 0 ? referencedCount * 100.0 / totalImages : 0;
        double currentUtilRounded = Math.round(currentUtil * 100.0) / 100.0;

        // 2. 查出候选图片（未引用 + 超过 beforeDays，按 created_at ASC 排序）
        List<Images> candidates = imagesMapper.selectCleanupCandidates(beforeDays);

        ImageCleanupVO result = new ImageCleanupVO();
        result.setDryRun(dryRun);

        if (dryRun) {
            // ==================== 预览模式 ====================
            result.setCurrentUtilization(currentUtilRounded);
            if (targetUtilization != null) {
                result.setTargetUtilization(targetUtilization);
            }

            List<Images> previewImages = new ArrayList<>();
            long removableSize = 0;

            for (Images img : candidates) {
                // 检查 targetUtilization：如果已经达到目标，停止
                if (targetUtilization != null && currentUtil >= targetUtilization) {
                    break;
                }
                previewImages.add(img);
                long size = img.getFileSize() != null ? img.getFileSize() : 0;
                removableSize += size;
                // 重新计算利用率
                currentUtil = referencedCount * 100.0 / (totalImages - previewImages.size());
            }

            double estimatedUtil = previewImages.isEmpty()
                    ? currentUtilRounded
                    : Math.round(currentUtil * 100.0) / 100.0;
            result.setEstimatedUtilization(estimatedUtil);
            result.setCount(previewImages.size());
            result.setTotalSize(removableSize);
            result.setImages(previewImages);
        } else {
            // ==================== 执行模式（物理删除） ====================
            double beforeUtil = currentUtilRounded;
            int deletedCount = 0;
            long freedSize = 0;

            for (Images img : candidates) {
                // 检查 targetUtilization
                if (targetUtilization != null) {
                    double curUtil = referencedCount * 100.0 / (totalImages - deletedCount);
                    if (curUtil >= targetUtilization) {
                        break;
                    }
                }
                // 物理删除磁盘文件
                fileStorage.delete(img.getStoragePath());
                // 物理删除 DB 记录
                imagesMapper.hardDeleteById(img.getId());
                deletedCount++;
                freedSize += img.getFileSize() != null ? img.getFileSize() : 0;
            }

            double afterUtil;
            if (deletedCount > 0) {
                afterUtil = Math.round(referencedCount * 100.0 / (totalImages - deletedCount) * 100.0) / 100.0;
            } else {
                afterUtil = beforeUtil;
            }

            result.setBeforeUtilization(beforeUtil);
            result.setAfterUtilization(afterUtil);
            result.setDeletedCount(deletedCount);
            result.setFreedSize(freedSize);
        }

        return ApiResponse.success(result);
    }

    @Operation(summary = "删除单张图片", description = "管理员强制删除，已被引用的图片无法删除（软删除）")
    @DeleteMapping("/{id}")
    public ApiResponse<?> deleteImage(@PathVariable Long id) {
        Images image = imagesMapper.selectById(id);
        if (image == null) {
            return ApiResponse.error(404, "图片不存在");
        }
        // 检查是否有引用
        List<ImageUsageVO> usages = imagesMapper.selectImageUsages(id);
        if (!usages.isEmpty()) {
            return ApiResponse.error(400, "该图片已被 " + usages.size() + " 处引用，无法删除");
        }
        imagesMapper.softDeleteById(id);
        return ApiResponse.success(null);
    }
}
