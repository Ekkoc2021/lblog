-- =====================================================
-- lblog 图片管理功能 — 建表 SQL
-- 执行方式: 在 MySQL 8 中 source 本文件 或逐行执行
-- =====================================================

CREATE TABLE IF NOT EXISTS images (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    url           VARCHAR(500)  NOT NULL COMMENT '访问URL',
    storage_path  VARCHAR(500)  NOT NULL COMMENT '存储路径（磁盘路径或OSS key）',
    original_name VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    mime_type     VARCHAR(50)   NOT NULL COMMENT 'MIME类型',
    file_size     BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    width         INT           DEFAULT NULL COMMENT '图片宽度',
    height        INT           DEFAULT NULL COMMENT '图片高度',
    md5           VARCHAR(32)   DEFAULT NULL COMMENT '文件MD5，用于去重',
    created_by    BIGINT        DEFAULT NULL COMMENT '上传者用户ID',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    DATETIME      DEFAULT NULL COMMENT '软删除时间',
    INDEX idx_md5 (md5),
    INDEX idx_url (url(191)),
    INDEX idx_created_by (created_by),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片库';

CREATE TABLE IF NOT EXISTS image_usages (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    image_id  BIGINT        NOT NULL COMMENT '图片ID',
    ref_type  VARCHAR(20)   NOT NULL COMMENT '引用类型：post / user / album / ...',
    ref_id    BIGINT        NOT NULL COMMENT '引用对象ID',
    field     VARCHAR(20)   NOT NULL COMMENT '引用字段：body / featured_image / avatar / cover / ...',
    INDEX idx_image_id (image_id),
    INDEX idx_ref (ref_type, ref_id),
    UNIQUE KEY uk_usage (image_id, ref_type, ref_id, field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片引用关系';
