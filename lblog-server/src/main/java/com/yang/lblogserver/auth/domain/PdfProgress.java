package com.yang.lblogserver.auth.domain;
import lombok.Data;
import java.util.Date;

@Data
public class PdfProgress {
    private Long id;
    private Long pdfId;
    private Long userId;
    private Integer pageNum;
    private Float scrollTop;
    private Date updatedAt;
}
