package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Schema(description = "PDF 文件夹视图")
public class PdfFolderVO {
    @Schema(description = "文件夹ID") private Long id;
    @Schema(description = "父文件夹ID") private Long parentId;
    @Schema(description = "文件夹名") private String name;
    @Schema(description = "排序") private Integer sortOrder;
    @Schema(description = "子文件夹") private List<PdfFolderVO> children = new ArrayList<>();
    @Schema(description = "创建时间") private Date createdAt;

    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getParentId() { return parentId; } public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public List<PdfFolderVO> getChildren() { return children; } public void setChildren(List<PdfFolderVO> children) { this.children = children; }
    public Date getCreatedAt() { return createdAt; } public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
