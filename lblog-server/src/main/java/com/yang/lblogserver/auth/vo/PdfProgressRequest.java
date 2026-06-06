package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "进度请求")
public class PdfProgressRequest {
    @NotNull @Schema(description = "页码") private Integer pageNum;
    @Schema(description = "滚动偏移") private Float scrollTop;
    public Integer getPageNum() { return pageNum; } public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Float getScrollTop() { return scrollTop; } public void setScrollTop(Float scrollTop) { this.scrollTop = scrollTop; }
}
