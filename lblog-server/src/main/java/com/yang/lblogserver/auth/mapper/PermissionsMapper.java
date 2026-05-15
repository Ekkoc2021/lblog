package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.Permissions;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionsMapper {

    List<Permissions> selectAll();

    Permissions selectByCode(@Param("code") String code);
}
