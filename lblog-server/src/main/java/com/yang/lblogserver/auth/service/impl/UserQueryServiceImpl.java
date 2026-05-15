package com.yang.lblogserver.auth.service.impl;

import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import com.yang.lblogserver.auth.service.UserQueryService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserQueryServiceImpl implements UserQueryService {
    private final UsersMapper usersMapper;
    public UserQueryServiceImpl(UsersMapper usersMapper) { this.usersMapper = usersMapper; }

    public Users findById(Long id) { return usersMapper.selectById(id); }

    public List<Users> selectBatchIds(List<Long> ids) { return usersMapper.selectBatchIds(ids); }

    public String findNicknameById(Long id) {
        Users user = usersMapper.selectById(id);
        return user != null ? user.getNickname() : null;
    }
}
