package com.yang.lblogserver.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "文章详情")
public class PostDetailVO extends PostVO {
    @Schema(description = "文章正文（Markdown）")
    private String body;
    @Schema(description = "上一篇")
    private PrevNextPostVO prevPost;
    @Schema(description = "下一篇")
    private PrevNextPostVO nextPost;

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public PrevNextPostVO getPrevPost() { return prevPost; }
    public void setPrevPost(PrevNextPostVO prevPost) { this.prevPost = prevPost; }
    public PrevNextPostVO getNextPost() { return nextPost; }
    public void setNextPost(PrevNextPostVO nextPost) { this.nextPost = nextPost; }
}
