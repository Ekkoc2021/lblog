package com.yang.lblogserver.mapper;

import com.yang.lblogserver.domain.Users;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UsersMapper {

    List<Users> selectBatchIds(List<Long> ids);
}
