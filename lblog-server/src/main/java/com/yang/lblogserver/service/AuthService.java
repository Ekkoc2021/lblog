package com.yang.lblogserver.service;

import com.yang.lblogserver.vo.LoginRequest;
import com.yang.lblogserver.vo.LoginResponse;
import com.yang.lblogserver.vo.UserInfoVO;

// TODO: 后续接入真实 JWT
public interface AuthService {

    /**
     * TODO: 当前为简单实现 — 查 DB 对比密码，返回固定格式 token
     * 后续改为 bcrypt 验证 + JWT 签发
     */
    LoginResponse login(LoginRequest request);

    /**
     * TODO: 当前为简单实现 — 从固定 token 中解析 userId
     * 后续改为 JWT 解析
     */
    UserInfoVO me(String token);

    /**
     * TODO: 后续接入 Token 黑名单机制
     */
    void logout(String token);

    /**
     * TODO: 临时从固定格式 token "lblog_{userId}" 提取 userId
     */
    Long parseUserId(String token);
}
