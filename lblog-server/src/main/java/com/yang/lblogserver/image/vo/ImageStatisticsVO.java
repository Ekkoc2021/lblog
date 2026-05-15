package com.yang.lblogserver.image.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图片统计概览 VO
 */
@Schema(description = "图片统计概览")
public class ImageStatisticsVO {

    @Schema(description = "图片总数")
    private long totalImages;

    @Schema(description = "总大小（字节）")
    private long totalSize;

    @Schema(description = "已引用数量")
    private long referencedCount;

    @Schema(description = "未引用数量")
    private long unreferencedCount;

    @Schema(description = "利用率（百分比）")
    private double utilizationRate;

    @Schema(description = "30天以上未引用数量")
    private long oldUnreferencedCount;

    @Schema(description = "30天以上未引用大小（字节）")
    private long oldUnreferencedSize;

    public long getTotalImages() { return totalImages; }
    public void setTotalImages(long totalImages) { this.totalImages = totalImages; }
    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public long getReferencedCount() { return referencedCount; }
    public void setReferencedCount(long referencedCount) { this.referencedCount = referencedCount; }
    public long getUnreferencedCount() { return unreferencedCount; }
    public void setUnreferencedCount(long unreferencedCount) { this.unreferencedCount = unreferencedCount; }
    public double getUtilizationRate() { return utilizationRate; }
    public void setUtilizationRate(double utilizationRate) { this.utilizationRate = utilizationRate; }
    public long getOldUnreferencedCount() { return oldUnreferencedCount; }
    public void setOldUnreferencedCount(long oldUnreferencedCount) { this.oldUnreferencedCount = oldUnreferencedCount; }
    public long getOldUnreferencedSize() { return oldUnreferencedSize; }
    public void setOldUnreferencedSize(long oldUnreferencedSize) { this.oldUnreferencedSize = oldUnreferencedSize; }
}
