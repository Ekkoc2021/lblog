package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import org.springframework.stereotype.Service;

@Service
public class UserStatusServiceImpl {

    private final UsersMapper usersMapper;

    public UserStatusServiceImpl(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }

    /**
     * 检查用户是否有效（存在且 status=1）
     */
    public boolean isUserActive(Long userId) {
        Users user = usersMapper.selectBatchIds(java.util.List.of(userId))
                .stream().findFirst().orElse(null);
        return user != null && user.getStatus() != null && user.getStatus() == 1;
    }
}
