package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文章详情")
public class PostDetailVO extends PostVO {
    @Schema(description = "文章正文（Markdown）")
    private String body;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
