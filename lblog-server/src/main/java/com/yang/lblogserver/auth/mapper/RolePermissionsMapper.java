package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.RolePermissions;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolePermissionsMapper {

    List<RolePermissions> selectByRoleId(@Param("roleId") Long roleId);

    int deleteByRoleId(@Param("roleId") Long roleId);

    int insertBatch(@Param("list") List<RolePermissions> list);
}
