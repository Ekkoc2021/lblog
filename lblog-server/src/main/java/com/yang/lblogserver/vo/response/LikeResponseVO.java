package com.yang.lblogserver.vo.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "点赞/取消点赞响应")
public class LikeResponseVO {
    @Schema(description = "是否已点赞")
    private boolean liked;
    @Schema(description = "当前点赞数")
    private int likeCount;

    public LikeResponseVO() {}

    public LikeResponseVO(boolean liked, int likeCount) {
        this.liked = liked;
        this.likeCount = likeCount;
    }

    public boolean isLiked() { return liked; }
    public void setLiked(boolean liked) { this.liked = liked; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
}
