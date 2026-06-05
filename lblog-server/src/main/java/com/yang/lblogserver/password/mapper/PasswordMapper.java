package com.yang.lblogserver.password.mapper;

import com.yang.lblogserver.password.domain.Password;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PasswordMapper {

    List<Password> selectByUserId(@Param("userId") Long userId,
                                  @Param("keyword") String keyword);

    Password selectById(@Param("id") Long id, @Param("userId") Long userId);

    int insert(Password password);

    int update(Password password);

    int softDelete(@Param("id") Long id,
                   @Param("userId") Long userId);
}
