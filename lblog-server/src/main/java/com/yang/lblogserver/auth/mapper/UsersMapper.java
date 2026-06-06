package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.Users;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UsersMapper {

    List<Users> selectBatchIds(List<Long> ids);

    // TODO: 后续可改为 Optional
    Users findByUsername(@Param("username") String username);

    Users findByEmail(@Param("email") String email);

    Users findByEmailExcludeId(@Param("email") String email, @Param("excludeId") Long excludeId);

    Users selectById(@Param("id") Long id);

    int insertUser(Users user);

    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    int updateLoginInfo(@Param("id") Long id);

    // ==================== 用户管理（管理端） ====================

    List<Users> selectUserList(@Param("keyword") String keyword,
                               @Param("status") Integer status,
                               @Param("inactiveDays") Integer inactiveDays,
                               @Param("role") String role,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    int countUserList(@Param("keyword") String keyword,
                      @Param("status") Integer status,
                      @Param("inactiveDays") Integer inactiveDays,
                      @Param("role") String role);

    int softDeleteUser(@Param("id") Long id);

    int updateUser(@Param("id") Long id,
                   @Param("nickname") String nickname,
                   @Param("email") String email,
                   @Param("status") Integer status,
                   @Param("role") String role);

    int countAll();

    int countAdminUsers();

    int countPostsByUserId(@Param("userId") Long userId);

    int updateRole(@Param("id") Long id, @Param("role") String role);
}
