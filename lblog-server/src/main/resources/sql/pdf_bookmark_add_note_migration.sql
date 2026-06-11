-- 线上迁移：给 pdf_bookmarks 表添加 note 列（书签笔记功能）
-- 对应提交 0cdc150：实体和 Mapper 已加 note 字段，但初始 DDL 未同步

ALTER TABLE pdf_bookmarks
    ADD COLUMN note TEXT NULL COMMENT '书签笔记' AFTER label;
