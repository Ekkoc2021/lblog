-- Migration: Add source_type column to pdf_files
-- Purpose: Distinguish between uploaded files (UPLOAD) and local-only files (LOCAL)
-- Run: mysql -u root iblog < pdf_local_source_type.sql
ALTER TABLE pdf_files ADD COLUMN source_type VARCHAR(10) DEFAULT 'UPLOAD' AFTER total_pages;
