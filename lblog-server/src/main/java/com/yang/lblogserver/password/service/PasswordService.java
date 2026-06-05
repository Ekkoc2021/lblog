package com.yang.lblogserver.password.service;

import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.password.vo.CreatePasswordRequest;
import com.yang.lblogserver.password.vo.PasswordVO;
import com.yang.lblogserver.password.vo.UpdatePasswordRequest;

public interface PasswordService {

    PageResult<PasswordVO> listPasswords(Long userId, int page, int pageSize, String keyword);

    PasswordVO createPassword(Long userId, CreatePasswordRequest req);

    PasswordVO updatePassword(Long userId, Long id, UpdatePasswordRequest req);

    void deletePassword(Long userId, Long id);
}
