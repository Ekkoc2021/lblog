package com.yang.lblogserver.domain;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 角色表
 */
@Data
public class Roles {
    private Long id;
    private String name;
    private String label;
    private String description;
    private Integer sortOrder;
    private Date createdAt;
    private Date updatedAt;
    private List<String> permissions;
}
