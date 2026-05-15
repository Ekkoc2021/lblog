package com.yang.lblogserver.image.domain;

import lombok.Data;
import java.util.Date;

/**
 * 图片库
 * @TableName images
 */
@Data
public class Images {
    /** 主键 */
    private Long id;

    /** 访问URL */
    private String url;

    /** 存储路径（磁盘路径或OSS key） */
    private String storagePath;

    /** 原始文件名 */
    private String originalName;

    /** MIME类型 */
    private String mimeType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 图片宽度 */
    private Integer width;

    /** 图片高度 */
    private Integer height;

    /** 文件MD5，用于去重 */
    private String md5;

    /** 上传者用户ID */
    private Long createdBy;

    /** 创建时间 */
    private Date createdAt;

    /** 软删除时间 */
    private Date deletedAt;
}
