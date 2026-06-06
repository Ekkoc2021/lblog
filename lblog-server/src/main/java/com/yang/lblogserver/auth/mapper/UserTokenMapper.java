package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.UserToken;
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

    List<UserToken> selectActiveSessions(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    int countActiveSessions(@Param("keyword") String keyword,
                            @Param("status") String status);

    int deleteExpiredTokens();

    int revokeById(@Param("id") Long id);

    int countValidByUserId(@Param("userId") Long userId);

    int updateReplacedBy(@Param("tokenHash") String tokenHash, @Param("replacedBy") String replacedBy);
}
