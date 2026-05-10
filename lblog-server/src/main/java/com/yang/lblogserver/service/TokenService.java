package com.yang.lblogserver.service;

import com.yang.lblogserver.security.model.LoginUser;
import com.yang.lblogserver.vo.response.TokenPairVO;

public interface TokenService {

    /** 登录成功: 签发双 token */
    TokenPairVO issueTokenPair(Long userId);

    /** 验证 access_token，返回用户信息 */
    LoginUser validateAccessToken(String rawToken);

    /** 验证 refresh_token 有效性，返回 userId */
    Long validateRefreshToken(String rawToken);

    /** 刷新 access_token (rotation) */
    TokenPairVO refreshAccessToken(String rawRefreshToken);

    /** 吊销单个 token */
    void revokeToken(String rawToken);

    /** 吊销用户所有 token */
    void revokeAllUserTokens(Long userId);
}
