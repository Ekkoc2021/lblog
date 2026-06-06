package com.yang.lblogserver.auth.vo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "标注保存请求")
public class PdfAnnotationRequest {
    @NotNull @Schema(description = "标注 JSON 数组") private String data;
    public String getData() { return data; } public void setData(String data) { this.data = data; }
}
