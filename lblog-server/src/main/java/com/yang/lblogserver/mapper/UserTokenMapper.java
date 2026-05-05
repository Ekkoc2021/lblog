package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.UserToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserTokenMapper {

    int insert(UserToken userToken);

    UserToken findByTokenHash(@Param("tokenHash") String tokenHash);

    UserToken findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<UserToken> findValidByUserId(@Param("userId") Long userId);

    List<UserToken> findByUserId(@Param("userId") Long userId);

    int revoke(@Param("tokenHash") String tokenHash);

    int revokeAllByUserId(@Param("userId") Long userId);

    int revokeAllByUserIdAndType(@Param("userId") Long userId, @Param("tokenType") String tokenType);

    int deleteExpired();

    int countValidByUserId(@Param("userId") Long userId);
}
