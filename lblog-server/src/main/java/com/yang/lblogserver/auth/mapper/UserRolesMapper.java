package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.UserRoles;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserRolesMapper {

    List<UserRoles> selectByUserId(@Param("userId") Long userId);

    int insert(UserRoles userRole);

    int deleteByUserId(@Param("userId") Long userId);

    int insertBatch(@Param("list") List<UserRoles> list);
}
