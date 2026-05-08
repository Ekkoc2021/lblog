package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UsersMapper {

    List<Users> selectBatchIds(List<Long> ids);

    // TODO: 后续可改为 Optional
    Users findByUsername(@Param("username") String username);

    Users findByEmail(@Param("email") String email);

    int insertUser(Users user);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);
}
