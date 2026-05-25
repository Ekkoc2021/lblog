package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.Roles;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RolesMapper {

    List<Roles> selectAll();

    Roles selectById(@Param("id") Long id);

    Roles selectByName(@Param("name") String name);

    int insert(Roles role);
}
