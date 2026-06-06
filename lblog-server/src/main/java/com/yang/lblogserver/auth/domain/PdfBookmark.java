package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfBookmark {
    private Long id;
    private Long pdfId;
    private Long userId;
    private Integer pageNum;
    private String label;
    private String note;
    private Date createdAt;
}
