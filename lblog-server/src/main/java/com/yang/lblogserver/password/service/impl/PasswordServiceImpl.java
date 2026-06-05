package com.yang.lblogserver.password.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.password.domain.Password;
import com.yang.lblogserver.password.mapper.PasswordMapper;
import com.yang.lblogserver.password.service.PasswordService;
import com.yang.lblogserver.password.vo.CreatePasswordRequest;
import com.yang.lblogserver.password.vo.PasswordVO;
import com.yang.lblogserver.password.vo.UpdatePasswordRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PasswordServiceImpl implements PasswordService {

    private final PasswordMapper passwordMapper;

    public PasswordServiceImpl(PasswordMapper passwordMapper) {
        this.passwordMapper = passwordMapper;
    }

    @Override
    public PageResult<PasswordVO> listPasswords(Long userId, int page, int pageSize, String keyword) {
        PageHelper.startPage(page, pageSize);
        List<Password> list = passwordMapper.selectByUserId(userId,
            keyword != null ? keyword.trim() : null);
        PageInfo<Password> pageInfo = new PageInfo<>(list);
        List<PasswordVO> vos = list.stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(page, pageSize, pageInfo.getTotal(), vos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordVO createPassword(Long userId, CreatePasswordRequest req) {
        Password entity = new Password();
        entity.setUserId(userId);
        entity.setSiteName(req.getSiteName());
        entity.setSiteUrl(req.getSiteUrl() != null ? req.getSiteUrl() : "");
        entity.setUsername(req.getUsername());
        entity.setEncryptedPassword(req.getEncryptedPassword());
        entity.setNote(req.getNote() != null ? req.getNote() : "");
        passwordMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordVO updatePassword(Long userId, Long id, UpdatePasswordRequest req) {
        Password entity = passwordMapper.selectById(id, userId);
        if (entity == null) return null;
        if (req.getSiteName() != null) entity.setSiteName(req.getSiteName());
        if (req.getSiteUrl() != null) entity.setSiteUrl(req.getSiteUrl());
        if (req.getUsername() != null) entity.setUsername(req.getUsername());
        if (req.getEncryptedPassword() != null) entity.setEncryptedPassword(req.getEncryptedPassword());
        if (req.getNote() != null) entity.setNote(req.getNote());
        passwordMapper.update(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePassword(Long userId, Long id) {
        int rows = passwordMapper.softDelete(id, userId);
        if (rows == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "密码记录不存在");
        }
    }

    private PasswordVO toVO(Password entity) {
        PasswordVO vo = new PasswordVO();
        vo.setId(entity.getId());
        vo.setSiteName(entity.getSiteName());
        vo.setSiteUrl(entity.getSiteUrl());
        vo.setUsername(entity.getUsername());
        vo.setEncryptedPassword(entity.getEncryptedPassword());
        vo.setNote(entity.getNote());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
