package com.yang.lblogserver.blog.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "点赞状态响应")
public class LikeStatusVO {
    @Schema(description = "是否已点赞")
    private boolean liked;

    public LikeStatusVO() {}

    public LikeStatusVO(boolean liked) {
        this.liked = liked;
    }

    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
}
