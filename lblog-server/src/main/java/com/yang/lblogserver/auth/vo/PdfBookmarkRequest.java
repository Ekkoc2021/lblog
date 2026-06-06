package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "书签请求")
public class PdfBookmarkRequest {
    @NotNull @Schema(description = "页码") private Integer pageNum;
    @NotBlank @Schema(description = "书签名") private String label;
    public Integer getPageNum() { return pageNum; } public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public String getLabel() { return label; } public void setLabel(String label) { this.label = label; }
}
