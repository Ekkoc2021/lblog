package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.Users;
import java.util.List;

public interface UserQueryService {
    Users findById(Long id);
    List<Users> selectBatchIds(List<Long> ids);
    String findNicknameById(Long id);
}
