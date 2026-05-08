package com.yang.lblogserver.domain;

import lombok.Data;

/**
 * 角色权限关联表
 */
@Data
public class RolePermissions {
    private Long id;
    private Long roleId;
    private Long permissionId;
}
