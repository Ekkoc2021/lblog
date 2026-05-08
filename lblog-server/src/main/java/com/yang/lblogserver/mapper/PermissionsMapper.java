package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Permissions;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionsMapper {

    List<Permissions> selectAll();

    Permissions selectByCode(@Param("code") String code);
}
