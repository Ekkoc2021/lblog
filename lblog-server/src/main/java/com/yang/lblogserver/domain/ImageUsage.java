package com.yang.lblogserver.domain;

import lombok.Data;

/**
 * 图片引用关系
 * @TableName image_usages
 */
@Data
public class ImageUsage {
    /** 主键 */
    private Long id;

    /** 图片ID */
    private Long imageId;

    /** 引用类型：post / user / album / ... */
    private String refType;

    /** 引用对象ID */
    private Long refId;

    /** 引用字段：body / featured_image / avatar / cover / ... */
    private String field;
}
