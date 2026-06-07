package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "PDF 文件视图")
public class PdfFileVO {
    @Schema(description = "文件ID") private Long id;
    @Schema(description = "文件夹ID") private Long folderId;
    @Schema(description = "原始文件名") private String originalName;
    @Schema(description = "文件大小(字节)") private Long fileSize;
    @Schema(description = "总页数") private Integer totalPages;
    @Schema(description = "来源类型: UPLOAD/LOCAL") private String sourceType;
    @Schema(description = "上传时间") private Date createdAt;
    @Schema(description = "更新时间") private Date updatedAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getFolderId() { return folderId; } public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getOriginalName() { return originalName; } public void setOriginalName(String originalName) { this.originalName = originalName; }
    public Long getFileSize() { return fileSize; } public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getTotalPages() { return totalPages; } public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public String getSourceType() { return sourceType; } public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Date getCreatedAt() { return createdAt; } public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
