package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
public class PdfFolder {
    private Long id;
    private Long userId;
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
    private List<PdfFolder> children;
}
