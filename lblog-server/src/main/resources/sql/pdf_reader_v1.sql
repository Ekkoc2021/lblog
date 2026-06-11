CREATE TABLE IF NOT EXISTS pdf_folders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    parent_id       BIGINT       NULL,
    name            VARCHAR(100) NOT NULL,
    sort_order      INT          DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_parent (parent_id),
    CONSTRAINT fk_folder_parent FOREIGN KEY (parent_id) REFERENCES pdf_folders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 文件夹';

CREATE TABLE IF NOT EXISTS pdf_files (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    folder_id       BIGINT       NULL,
    filename        VARCHAR(255) NOT NULL COMMENT '存储文件名(UUID)',
    original_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size       BIGINT       NOT NULL COMMENT '字节数',
    file_path       VARCHAR(500) NOT NULL COMMENT '服务器物理路径',
    total_pages     INT          DEFAULT 0 COMMENT 'PDF 总页数',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_folder (user_id, folder_id),
    INDEX idx_user (user_id),
    CONSTRAINT fk_file_folder FOREIGN KEY (folder_id) REFERENCES pdf_folders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 文件';

CREATE TABLE IF NOT EXISTS pdf_annotations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    page_num        INT          NOT NULL COMMENT '页码(1-based)',
    user_id         BIGINT       NOT NULL,
    data            JSON         NOT NULL COMMENT 'DokFlow 标注 JSON 数组',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_page_user (pdf_id, page_num, user_id),
    CONSTRAINT fk_ann_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 标注(按页)';

CREATE TABLE IF NOT EXISTS pdf_bookmarks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    page_num        INT          NOT NULL,
    label           VARCHAR(100) NOT NULL,
    note            TEXT         NULL     COMMENT '书签笔记',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pdf_user (pdf_id, user_id),
    CONSTRAINT fk_bm_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 书签';

CREATE TABLE IF NOT EXISTS pdf_progress (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    pdf_id          BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    page_num        INT          DEFAULT 1,
    scroll_top      FLOAT        DEFAULT 0 COMMENT '页内滚动偏移(px)',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pdf_user (pdf_id, user_id),
    CONSTRAINT fk_prog_file FOREIGN KEY (pdf_id) REFERENCES pdf_files(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDF 阅读进度';
