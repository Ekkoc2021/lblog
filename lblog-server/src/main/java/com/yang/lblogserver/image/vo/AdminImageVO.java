package com.yang.lblogserver.image.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;
import java.util.List;

/**
 * 管理端图片列表 VO（含引用次数和引用详情）
 */
@Schema(description = "管理端图片列表项")
public class AdminImageVO {

    @Schema(description = "图片ID")
    private Long id;

    @Schema(description = "访问URL")
    private String url;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "MIME类型")
    private String mimeType;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;

    @Schema(description = "引用次数")
    private int usageCount;

    @Schema(description = "引用详情列表")
    private List<ImageUsageVO> usages;

    @Schema(description = "上传者用户ID")
    private Long createdBy;

    @Schema(description = "创建时间")
    private Date createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    public List<ImageUsageVO> getUsages() { return usages; }
    public void setUsages(List<ImageUsageVO> usages) { this.usages = usages; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
