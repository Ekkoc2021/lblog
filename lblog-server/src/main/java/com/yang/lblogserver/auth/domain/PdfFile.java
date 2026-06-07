package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfFile {
    private Long id;
    private Long userId;
    private Long folderId;
    private String filename;
    private String originalName;
    private Long fileSize;
    private String filePath;
    private Integer totalPages;
    private String sourceType;
    private Date createdAt;
    private Date updatedAt;
}
