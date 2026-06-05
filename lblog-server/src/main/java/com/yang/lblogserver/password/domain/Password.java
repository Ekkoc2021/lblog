package com.yang.lblogserver.password.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.Date;

@Getter
@Setter
@ToString
public class Password {
    private Long id;
    private Long userId;
    private String siteName;
    private String siteUrl;
    private String username;
    private String encryptedPassword;
    private String note;
    private Boolean isDeleted;
    private Date createdAt;
    private Date updatedAt;
}
