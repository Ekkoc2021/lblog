package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "创建标签请求")
public class CreateTagRequest {
    @NotBlank(message = "标签名不能为空")
    @Schema(description = "标签名")
    private String name;

    @NotBlank(message = "URL别名不能为空")
    @Schema(description = "URL别名")
    private String slug;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
