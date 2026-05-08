package com.yang.lblogserver.vo.admin;

import com.yang.lblogserver.domain.Images;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 图片清理响应 VO
 *
 * <p>dryRun=true 时返回预览信息：currentUtilization, targetUtilization, estimatedUtilization, count, totalSize, images
 * <br>dryRun=false 时返回执行结果：beforeUtilization, afterUtilization, deletedCount, freedSize
 */
@Schema(description = "图片清理响应")
public class ImageCleanupVO {

    @Schema(description = "是否为预览模式")
    private boolean dryRun;

    // ==================== 预览模式（dryRun=true） ====================

    @Schema(description = "当前利用率（百分比，仅 dryRun=true 时）")
    private Double currentUtilization;

    @Schema(description = "目标利用率（百分比，传了才返回，仅 dryRun=true 时）")
    private Double targetUtilization;

    @Schema(description = "清理后的预计利用率（百分比，仅 dryRun=true 时）")
    private Double estimatedUtilization;

    @Schema(description = "将被清理的图片数（仅 dryRun=true 时）")
    private int count;

    @Schema(description = "可释放的空间（字节，仅 dryRun=true 时）")
    private long totalSize;

    @Schema(description = "候选图片列表（仅 dryRun=true 时）")
    private List<Images> images;

    // ==================== 执行模式（dryRun=false） ====================

    @Schema(description = "清理前利用率（百分比，仅 dryRun=false 时）")
    private Double beforeUtilization;

    @Schema(description = "清理后利用率（百分比，仅 dryRun=false 时）")
    private Double afterUtilization;

    @Schema(description = "实际删除数量（仅 dryRun=false 时）")
    private Integer deletedCount;

    @Schema(description = "实际释放大小（字节，仅 dryRun=false 时）")
    private Long freedSize;

    // ==================== Getters & Setters ====================

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public Double getCurrentUtilization() { return currentUtilization; }
    public void setCurrentUtilization(Double currentUtilization) { this.currentUtilization = currentUtilization; }

    public Double getTargetUtilization() { return targetUtilization; }
    public void setTargetUtilization(Double targetUtilization) { this.targetUtilization = targetUtilization; }

    public Double getEstimatedUtilization() { return estimatedUtilization; }
    public void setEstimatedUtilization(Double estimatedUtilization) { this.estimatedUtilization = estimatedUtilization; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public List<Images> getImages() { return images; }
    public void setImages(List<Images> images) { this.images = images; }

    public Double getBeforeUtilization() { return beforeUtilization; }
    public void setBeforeUtilization(Double beforeUtilization) { this.beforeUtilization = beforeUtilization; }

    public Double getAfterUtilization() { return afterUtilization; }
    public void setAfterUtilization(Double afterUtilization) { this.afterUtilization = afterUtilization; }

    public Integer getDeletedCount() { return deletedCount; }
    public void setDeletedCount(Integer deletedCount) { this.deletedCount = deletedCount; }

    public Long getFreedSize() { return freedSize; }
    public void setFreedSize(Long freedSize) { this.freedSize = freedSize; }
}
