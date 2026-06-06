package com.yang.lblogserver.auth.domain;

import lombok.Data;
import java.util.Date;

@Data
public class AuthorApplication {
    private Long id;
    private Long userId;
    private String reason;
    private Integer status;     // 0=待审核 1=通过 2=拒绝 3=需补充
    private String feedback;
    private Long reviewedBy;
    private Date reviewedAt;
    private Date createdAt;
    private Date updatedAt;
}
