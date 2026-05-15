package com.yang.lblogserver.auth.domain;

import lombok.Data;
import java.util.Date;

/**
 * 权限表
 */
@Data
public class Permissions {
    private Long id;
    private String code;
    private String label;
    private String module;
    private Date createdAt;
}
