package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfAnnotation {
    private Long id;
    private Long pdfId;
    private Integer pageNum;
    private Long userId;
    private String data;
    private Date createdAt;
    private Date updatedAt;
}
