package com.yang.lblogserver.domain;

import lombok.Data;

/**
 * 用户角色关联表
 */
@Data
public class UserRoles {
    private Long id;
    private Long userId;
    private Long roleId;
}
