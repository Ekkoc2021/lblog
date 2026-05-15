package com.yang.lblogserver.blog.vo.admin;

import com.yang.lblogserver.blog.vo.CommentVO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端评论（含文章标题）")
public class AdminCommentVO extends CommentVO {

    @Schema(description = "所属文章标题")
    private String postTitle;

    @Schema(description = "所属文章 slug")
    private String postSlug;

    public String getPostTitle() { return postTitle; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; }
    public String getPostSlug() { return postSlug; }
    public void setPostSlug(String postSlug) { this.postSlug = postSlug; }
}
