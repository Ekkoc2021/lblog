package com.yang.lblogserver.service.impl;

import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.service.AuthService;
import com.yang.lblogserver.vo.LoginRequest;
import com.yang.lblogserver.vo.LoginResponse;
import com.yang.lblogserver.vo.UserInfoVO;
import org.springframework.stereotype.Service;

// TODO: ─────────────────────────────────────────────
// TODO: 临时鉴权实现，后续需替换为：
// TODO: 1. bcrypt 密码验证（引入 spring-security-crypto）
// TODO: 2. JWT 签发与解析（引入 jjwt 依赖）
// TODO: 3. Token 黑名单 / 刷新机制
// TODO: ─────────────────────────────────────────────
@Service
public class AuthServiceImpl implements AuthService {

    private final UsersMapper usersMapper;

    public AuthServiceImpl(UsersMapper usersMapper) {
        this.usersMapper = usersMapper;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO: 临时直接比较 password_hash，后续改为 bcrypt 验证
        Users user = usersMapper.findByUsername(request.getUsername());
//        if (user == null || !request.getPassword().equals(user.getPasswordHash())) {
//            return null;
//        }
        // TODO: 校验用户状态（status=1）
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }

        // TODO: 临时固定格式 token "lblog_{userId}"，后续替换为 JWT
        String token = "lblog_" + user.getId();
        return new LoginResponse(token, toUserInfo(user));
    }

    @Override
    public UserInfoVO me(String token) {
        // TODO: 临时从固定格式解析 userId，后续改为 JWT 解析
        if (token == null || !token.startsWith("lblog_")) {
            return null;
        }
        try {
            Long userId = Long.parseLong(token.substring(6));
            Users user = usersMapper.selectBatchIds(java.util.List.of(userId))
                    .stream().findFirst().orElse(null);
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return null;
            }
            return toUserInfo(user);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void logout(String token) {
        // TODO: 后续接入 Token 黑名单
    }

    @Override
    public Long parseUserId(String token) {
        if (token == null || !token.startsWith("lblog_")) {
            return null;
        }
        try {
            return Long.parseLong(token.substring(6));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserInfoVO toUserInfo(Users user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        return vo;
    }
}
