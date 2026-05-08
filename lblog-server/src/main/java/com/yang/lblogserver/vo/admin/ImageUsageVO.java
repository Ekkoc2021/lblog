package com.yang.lblogserver.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图片引用信息 VO
 */
@Schema(description = "图片引用信息")
public class ImageUsageVO {

    @Schema(description = "引用类型: post/user/album")
    private String refType;

    @Schema(description = "引用对象ID")
    private Long refId;

    @Schema(description = "引用字段: body/featured_image/avatar/cover")
    private String field;

    @Schema(description = "引用标题（如文章标题）")
    private String refTitle;

    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getRefTitle() { return refTitle; }
    public void setRefTitle(String refTitle) { this.refTitle = refTitle; }
}
