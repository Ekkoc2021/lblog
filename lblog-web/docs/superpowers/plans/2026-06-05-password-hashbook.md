# 密码本功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在博客工具箱中新增密码本功能，支持加密存储各网站密码，前端 AES-256-GCM 加密后存后端，密文不存服务端。

**Architecture:** 后端新增 `password` 模块（参考 todo 模块），前端新增 `HashbookPanel` 浮动面板组件（参考 TodoPanel），加密用浏览器 Web Crypto API。后端只存密文不做加解密。

**Tech Stack:** Java 21 + Spring Boot 3.5 + MyBatis + MySQL 8 / React 19 + TypeScript 6 + Ant Design 6 + Web Crypto API

---

## 1. 后端 — 数据库建表

### Task 1: 创建 SQL 建表脚本

**Files:**
- Create: `lblog-server/src/main/resources/sql/password_v1.sql`

- [ ] **Step 1: 写入建表 SQL**

```sql
-- lblog 密码本功能 v1 — 建表 SQL

CREATE TABLE IF NOT EXISTS passwords (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id             BIGINT NOT NULL COMMENT '用户ID',
    site_name           VARCHAR(100) NOT NULL COMMENT '网站名称',
    site_url            VARCHAR(500) DEFAULT '' COMMENT '网址',
    username            VARCHAR(200) NOT NULL COMMENT '账号',
    encrypted_password  TEXT NOT NULL COMMENT 'AES-256-GCM 加密后的密码',
    note                VARCHAR(500) DEFAULT '' COMMENT '备注',
    is_deleted          TINYINT(1) DEFAULT 0 COMMENT '软删除标记',
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_passwords_user (user_id),
    INDEX idx_passwords_user_deleted (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='密码本表';
```

- [ ] **Step 2: 在 MySQL 执行建表**

使用 MySQL MCP:
```sql
source lblog-server/src/main/resources/sql/password_v1.sql
```

或直接执行 CREATE TABLE 语句。

- [ ] **Step 3: Commit**

```bash
git add lblog-server/src/main/resources/sql/password_v1.sql
git commit -m "feat: add passwords table for hashbook feature"
```

---

## 2. 后端 — Domain + Mapper

### Task 2: 创建 Password 实体

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/domain/Password.java`

- [ ] **Step 1: 写入实体类**

```java
package com.yang.lblogserver.password.domain;

import lombok.Data;
import java.util.Date;

@Data
public class Password {
    private Long id;
    private Long userId;
    private String siteName;
    private String siteUrl;
    private String username;
    private String encryptedPassword;
    private String note;
    private Boolean isDeleted;
    private Date createdAt;
    private Date updatedAt;
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/password/domain/Password.java
git commit -m "feat: add Password domain entity"
```

### Task 3: 创建 PasswordMapper 接口和 XML

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/mapper/PasswordMapper.java`
- Create: `lblog-server/src/main/resources/com/yang/lblogserver/password/mapper/PasswordMapper.xml`

- [ ] **Step 1: 写入 Mapper 接口**

```java
package com.yang.lblogserver.password.mapper;

import com.yang.lblogserver.password.domain.Password;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PasswordMapper {

    List<Password> selectByUserId(@Param("userId") Long userId,
                                  @Param("keyword") String keyword);

    Password selectById(@Param("id") Long id);

    int insert(Password password);

    int update(Password password);

    int softDelete(@Param("id") Long id,
                   @Param("userId") Long userId);
}
```

- [ ] **Step 2: 写入 Mapper XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.password.mapper.PasswordMapper">

    <resultMap id="PasswordResult" type="com.yang.lblogserver.password.domain.Password">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="siteName" column="site_name"/>
        <result property="siteUrl" column="site_url"/>
        <result property="username" column="username"/>
        <result property="encryptedPassword" column="encrypted_password"/>
        <result property="note" column="note"/>
        <result property="isDeleted" column="is_deleted"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <select id="selectByUserId" resultMap="PasswordResult">
        SELECT * FROM passwords
        WHERE user_id = #{userId} AND is_deleted = 0
        <if test="keyword != null and keyword != ''">
            AND (site_name LIKE CONCAT('%', #{keyword}, '%')
                 OR username LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        ORDER BY updated_at DESC
    </select>

    <select id="selectById" resultMap="PasswordResult">
        SELECT * FROM passwords WHERE id = #{id} AND is_deleted = 0
    </select>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO passwords (user_id, site_name, site_url, username, encrypted_password, note)
        VALUES (#{userId}, #{siteName}, #{siteUrl}, #{username}, #{encryptedPassword}, #{note})
    </insert>

    <update id="update">
        UPDATE passwords
        <set>
            <if test="siteName != null">site_name = #{siteName},</if>
            <if test="siteUrl != null">site_url = #{siteUrl},</if>
            <if test="username != null">username = #{username},</if>
            <if test="encryptedPassword != null">encrypted_password = #{encryptedPassword},</if>
            <if test="note != null">note = #{note},</if>
        </set>
        WHERE id = #{id} AND is_deleted = 0
    </update>

    <update id="softDelete">
        UPDATE passwords SET is_deleted = 1
        WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0
    </update>
</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/password/mapper/PasswordMapper.java
git add lblog-server/src/main/resources/com/yang/lblogserver/password/mapper/PasswordMapper.xml
git commit -m "feat: add PasswordMapper with search and soft-delete"
```

---

## 3. 后端 — VO 类

### Task 4: 创建 Request/Response VO

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/vo/CreatePasswordRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/vo/UpdatePasswordRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/vo/PasswordVO.java`

- [ ] **Step 1: 写入 CreatePasswordRequest**

```java
package com.yang.lblogserver.password.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建密码记录请求")
public class CreatePasswordRequest {

    @NotBlank @Size(max = 100)
    @Schema(description = "网站名称", required = true)
    private String siteName;

    @Schema(description = "网址")
    private String siteUrl;

    @NotBlank @Size(max = 200)
    @Schema(description = "账号", required = true)
    private String username;

    @NotBlank
    @Schema(description = "加密后的密码密文", required = true)
    private String encryptedPassword;

    @Schema(description = "备注")
    private String note;

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
```

- [ ] **Step 2: 写入 UpdatePasswordRequest**

```java
package com.yang.lblogserver.password.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "更新密码记录请求")
public class UpdatePasswordRequest {

    @Size(max = 100)
    @Schema(description = "网站名称")
    private String siteName;

    @Schema(description = "网址")
    private String siteUrl;

    @Size(max = 200)
    @Schema(description = "账号")
    private String username;

    @Schema(description = "加密后的密码密文")
    private String encryptedPassword;

    @Schema(description = "备注")
    private String note;

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
```

- [ ] **Step 3: 写入 PasswordVO**

```java
package com.yang.lblogserver.password.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "密码记录")
public class PasswordVO {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "网站名称")
    private String siteName;

    @Schema(description = "网址")
    private String siteUrl;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "加密后的密码密文")
    private String encryptedPassword;

    @Schema(description = "备注")
    private String note;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getSiteUrl() { return siteUrl; }
    public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/password/vo/
git commit -m "feat: add password VO classes (create/update/response)"
```

---

## 4. 后端 — Service 层

### Task 5: 创建 PasswordService 接口和实现

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/service/PasswordService.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/service/impl/PasswordServiceImpl.java`

- [ ] **Step 1: 写入 Service 接口**

```java
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
```

- [ ] **Step 2: 写入 ServiceImpl**

```java
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
        List<Password> list = passwordMapper.selectByUserId(userId, keyword);
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
        Password entity = passwordMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) return null;
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
        passwordMapper.softDelete(id, userId);
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
```

- [ ] **Step 3: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/password/service/
git commit -m "feat: add PasswordService with CRUD and pagination"
```

---

## 5. 后端 — Controller 层

### Task 6: 创建 PasswordController

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/password/controller/PasswordController.java`

- [ ] **Step 1: 写入 Controller**

```java
package com.yang.lblogserver.password.controller;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.password.service.PasswordService;
import com.yang.lblogserver.password.vo.CreatePasswordRequest;
import com.yang.lblogserver.password.vo.PasswordVO;
import com.yang.lblogserver.password.vo.UpdatePasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "密码本", description = "个人密码本管理")
@Validated
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public class PasswordController {

    private final PasswordService passwordService;

    public PasswordController(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser user) {
            return user.getUserId();
        }
        return null;
    }

    @GetMapping("/passwords")
    @Operation(summary = "获取密码本列表")
    public ApiResponse<PageResult<PasswordVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(passwordService.listPasswords(getCurrentUserId(), page, pageSize, keyword));
    }

    @PostMapping("/passwords")
    @Operation(summary = "新增密码记录")
    public ApiResponse<PasswordVO> create(@Valid @RequestBody CreatePasswordRequest req) {
        return ApiResponse.success(passwordService.createPassword(getCurrentUserId(), req));
    }

    @PutMapping("/passwords/{id}")
    @Operation(summary = "更新密码记录")
    public ApiResponse<PasswordVO> update(@PathVariable Long id, @Valid @RequestBody UpdatePasswordRequest req) {
        PasswordVO result = passwordService.updatePassword(getCurrentUserId(), id, req);
        if (result == null) return ApiResponse.error(404, "密码记录不存在");
        return ApiResponse.success(result);
    }

    @DeleteMapping("/passwords/{id}")
    @Operation(summary = "删除密码记录（软删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        passwordService.deletePassword(getCurrentUserId(), id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 2: 编译验证**

使用 IntelliJ MCP: `mcp__idea__build_project(rebuild: false)`

- [ ] **Step 3: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/password/controller/PasswordController.java
git commit -m "feat: add PasswordController with CRUD and search endpoints"
```

---

## 6. 前端 — Types

### Task 7: 添加密码本 TypeScript 类型

**Files:**
- Modify: `lblog-web/src/types/index.ts` — 在文件末尾追加

- [ ] **Step 1: 追加类型定义**

```typescript
// 密码本
export interface PasswordEntry {
  id: number;
  siteName: string;
  siteUrl: string;
  username: string;
  encryptedPassword: string;
  note: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePasswordEntryRequest {
  siteName: string;
  siteUrl?: string;
  username: string;
  encryptedPassword: string;
  note?: string;
}

export interface UpdatePasswordEntryRequest {
  siteName?: string;
  siteUrl?: string;
  username?: string;
  encryptedPassword?: string;
  note?: string;
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/types/index.ts
git commit -m "feat: add PasswordEntry types for hashbook"
```

---

## 7. 前端 — Crypto 加密工具

### Task 8: 创建前端加解密工具函数

**Files:**
- Create: `lblog-web/src/utils/crypto.ts`

- [ ] **Step 1: 写入 crypto 工具**

```typescript
/**
 * AES-256-GCM 加解密工具，用于密码本。
 * 密钥通过 PBKDF2 从用户密文派生，salt 随机生成。
 * 密文格式：base64(salt):base64(iv):base64(ciphertext)
 */

const ALGORITHM = 'AES-GCM';
const KEY_LENGTH = 256;
const SALT_LENGTH = 16;
const IV_LENGTH = 12;
const PBKDF2_ITERATIONS = 200_000;

function base64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    bytes[i] = bin.charCodeAt(i);
  }
  return bytes;
}

function bytesToBase64(bytes: Uint8Array): string {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin);
}

async function deriveKey(secret: string, salt: Uint8Array): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    'raw', enc.encode(secret), 'PBKDF2', false, ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    keyMaterial,
    { name: ALGORITHM, length: KEY_LENGTH },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function encrypt(plaintext: string, secret: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
  const iv = crypto.getRandomValues(new Uint8Array(IV_LENGTH));
  const key = await deriveKey(secret, salt);
  const enc = new TextEncoder();
  const ciphertext = await crypto.subtle.encrypt(
    { name: ALGORITHM, iv },
    key,
    enc.encode(plaintext)
  );
  return `${bytesToBase64(salt)}:${bytesToBase64(iv)}:${bytesToBase64(new Uint8Array(ciphertext))}`;
}

export async function decrypt(ciphertext: string, secret: string): Promise<string> {
  const parts = ciphertext.split(':');
  if (parts.length !== 3) throw new Error('无效的密文格式');
  const salt = base64ToBytes(parts[0]);
  const iv = base64ToBytes(parts[1]);
  const data = base64ToBytes(parts[2]);
  const key = await deriveKey(secret, salt);
  const dec = new TextDecoder();
  const plaintext = await crypto.subtle.decrypt(
    { name: ALGORITHM, iv },
    key,
    data
  );
  return dec.decode(plaintext);
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/utils/crypto.ts
git commit -m "feat: add AES-256-GCM encrypt/decrypt utilities for hashbook"
```

---

## 8. 前端 — API Service

### Task 9: 创建密码本 API 服务

**Files:**
- Create: `lblog-web/src/services/passwordApi.ts`

- [ ] **Step 1: 写入 API 服务**

```typescript
import { request, buildQuery } from './api';
import type { ApiResponse, PageResult, PasswordEntry, CreatePasswordEntryRequest, UpdatePasswordEntryRequest } from '../types';

export function getPasswords(params?: {
  page?: number;
  pageSize?: number;
  keyword?: string;
}): Promise<ApiResponse<PageResult<PasswordEntry>>> {
  return request<PageResult<PasswordEntry>>(`/api/v1/passwords${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export function createPassword(data: CreatePasswordEntryRequest): Promise<ApiResponse<PasswordEntry>> {
  return request<PasswordEntry>('/api/v1/passwords', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export function updatePassword(id: number, data: UpdatePasswordEntryRequest): Promise<ApiResponse<PasswordEntry>> {
  return request<PasswordEntry>(`/api/v1/passwords/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export function deletePassword(id: number): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/passwords/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/services/passwordApi.ts
git commit -m "feat: add password API service for hashbook"
```

---

## 9. 前端 — HashbookPanel 主组件

### Task 10: 创建密码本面板主组件

**Files:**
- Create: `lblog-web/src/components/HashbookPanel/index.tsx`

- [ ] **Step 1: 写入面板组件**

```tsx
import { useState, useEffect, useRef, useCallback } from 'react';
import { List, Input, Button, message, Pagination, theme, Space, Popconfirm } from 'antd';
import { PlusOutlined, CloseOutlined, KeyOutlined, SearchOutlined, CopyOutlined, DeleteOutlined, EditOutlined, EyeOutlined } from '@ant-design/icons';
import { getPasswords, deletePassword } from '../../services/passwordApi';
import PasswordModal from './PasswordModal';
import ViewPasswordModal from './ViewPasswordModal';
import type { PasswordEntry } from '../../types';

interface Props {
  onClose: () => void;
}

const PAGE_SIZE = 20;

const HashbookPanel: React.FC<Props> = ({ onClose }) => {
  const { token } = theme.useToken();
  const [entries, setEntries] = useState<PasswordEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<PasswordEntry | null>(null);
  const [viewingEntry, setViewingEntry] = useState<PasswordEntry | null>(null);

  const [pos, setPos] = useState(() => {
    try {
      const saved = localStorage.getItem('hashbookPanelPos');
      if (saved) { const p = JSON.parse(saved); if (p && typeof p.left === 'number') return p; }
    } catch { /* ignore */ }
    return { left: Math.round((window.innerWidth - 400) / 2) + 60, top: Math.round(window.innerHeight / 5) + 40 };
  });
  const dragging = useRef(false);
  const dragStart = useRef({ x: 0, y: 0, left: 0, top: 0 });

  const fetchData = useCallback(async (p: number, kw: string) => {
    setLoading(true);
    try {
      const res = await getPasswords({ page: p, pageSize: PAGE_SIZE, keyword: kw || undefined });
      setEntries(res.data!.list);
      setTotal(res.data!.total);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
      setEntries([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchData(page, keyword); }, [page, keyword, fetchData]);

  const handleSearch = (value: string) => {
    setKeyword(value);
    setPage(1);
  };

  const handleDelete = async (id: number) => {
    try {
      await deletePassword(id);
      message.success('已删除');
      fetchData(page, keyword);
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    }
  };

  const handleSaveSuccess = () => {
    setModalOpen(false);
    setEditingEntry(null);
    fetchData(page, keyword);
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(`${label}已复制`));
  };

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragging.current = true;
    dragStart.current = { x: e.clientX, y: e.clientY, left: pos.left, top: pos.top };
    let lastClientX = e.clientX;
    let lastClientY = e.clientY;
    const onMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      lastClientX = ev.clientX;
      lastClientY = ev.clientY;
      setPos({
        left: Math.max(0, Math.min(window.innerWidth - 400, dragStart.current.left + ev.clientX - dragStart.current.x)),
        top: Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + ev.clientY - dragStart.current.y)),
      });
    };
    const onUp = () => {
      dragging.current = false;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      const newLeft = Math.max(0, Math.min(window.innerWidth - 400, dragStart.current.left + lastClientX - dragStart.current.x));
      const newTop = Math.max(0, Math.min(window.innerHeight - 100, dragStart.current.top + lastClientY - dragStart.current.y));
      try { localStorage.setItem('hashbookPanelPos', JSON.stringify({ left: newLeft, top: newTop })); } catch { /* ignore */ }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [pos]);

  return (
    <div style={{
      position: 'fixed',
      left: pos.left,
      top: pos.top,
      width: 400,
      maxHeight: '75vh',
      zIndex: 1000,
      background: token.colorBgElevated,
      borderRadius: 12,
      boxShadow: token.boxShadowSecondary,
      display: 'flex',
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Header — draggable */}
      <div onMouseDown={handleMouseDown} style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '10px 16px', cursor: 'grab', userSelect: 'none',
        borderBottom: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0,
      }}>
        <span style={{ fontWeight: 600, fontSize: 15, color: token.colorText }}>
          <KeyOutlined style={{ marginRight: 8 }} />
          密码本
          <span style={{ marginLeft: 8, fontSize: 12, color: token.colorTextTertiary, fontWeight: 400 }}>
            {total}
          </span>
        </span>
        <CloseOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={onClose} />
      </div>

      {/* Search + Add bar */}
      <div style={{ padding: '8px 16px', flexShrink: 0, display: 'flex', gap: 8 }}>
        <Input
          prefix={<SearchOutlined />}
          placeholder="搜索站点或账号..."
          allowClear
          onPressEnter={(e) => handleSearch((e.target as HTMLInputElement).value)}
          onClear={() => handleSearch('')}
          style={{ flex: 1 }}
        />
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingEntry(null); setModalOpen(true); }}>新增</Button>
      </div>
      <div style={{ fontSize: 11, color: token.colorTextTertiary, padding: '0 16px 4px', flexShrink: 0 }}>
        密文不上传后端，请自行妥善保管
      </div>

      {/* List */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px' }}>
        <List
          loading={loading}
          dataSource={entries}
          locale={{ emptyText: '暂无记录' }}
          renderItem={(item) => (
            <List.Item
              style={{ padding: '10px 0', cursor: 'pointer' }}
              onClick={() => setViewingEntry(item)}
              actions={[
                <Button key="edit" type="link" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setEditingEntry(item); setModalOpen(true); }} />,
                <Popconfirm key="del" title="确定删除？" onConfirm={(e) => { e?.stopPropagation(); handleDelete(item.id); }} onCancel={(e) => e?.stopPropagation()}>
                  <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={(e) => e.stopPropagation()} />
                </Popconfirm>,
              ]}
            >
              <List.Item.Meta
                title={
                  <Space size={4}>
                    <span style={{ fontWeight: 500 }}>{item.siteName}</span>
                    <span style={{ fontSize: 12, color: token.colorTextTertiary }}>
                      {item.username}
                    </span>
                  </Space>
                }
                description={
                  <Space size={8} style={{ fontSize: 12 }}>
                    {item.siteUrl && <a href={item.siteUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()} style={{ color: token.colorPrimary }}>{item.siteUrl}</a>}
                    {!item.siteUrl && <span style={{ color: token.colorTextTertiary }}>—</span>}
                    <CopyOutlined style={{ cursor: 'pointer', color: token.colorTextTertiary }} onClick={(e) => { e.stopPropagation(); copyToClipboard(item.username, '账号'); }} />
                  </Space>
                }
              />
            </List.Item>
          )}
        />
      </div>

      {/* Pagination */}
      {total > PAGE_SIZE && (
        <div style={{ padding: '8px 16px', borderTop: `1px solid ${token.colorBorderSecondary}`, flexShrink: 0, textAlign: 'center' }}>
          <Pagination
            size="small"
            current={page}
            pageSize={PAGE_SIZE}
            total={total}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      )}

      {/* Create/Edit Modal */}
      <PasswordModal
        open={modalOpen}
        entry={editingEntry}
        onClose={() => { setModalOpen(false); setEditingEntry(null); }}
        onSuccess={handleSaveSuccess}
      />

      {/* View Modal */}
      {viewingEntry && (
        <ViewPasswordModal
          entry={viewingEntry}
          onClose={() => setViewingEntry(null)}
        />
      )}
    </div>
  );
};

export default HashbookPanel;
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/components/HashbookPanel/index.tsx
git commit -m "feat: add HashbookPanel main component with list, search, pagination"
```

---

## 10. 前端 — PasswordModal（新增/编辑弹窗）

### Task 11: 创建新增/编辑密码弹窗

**Files:**
- Create: `lblog-web/src/components/HashbookPanel/PasswordModal.tsx`

- [ ] **Step 1: 写入 Modal 组件**

```tsx
import { useState, useEffect } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { createPassword, updatePassword } from '../../services/passwordApi';
import { encrypt } from '../../utils/crypto';
import type { PasswordEntry } from '../../types';

interface Props {
  open: boolean;
  entry: PasswordEntry | null;
  onClose: () => void;
  onSuccess: () => void;
}

const PasswordModal: React.FC<Props> = ({ open, entry, onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const isEdit = !!entry;

  useEffect(() => {
    if (open) {
      if (entry) {
        form.setFieldsValue({
          siteName: entry.siteName,
          siteUrl: entry.siteUrl,
          username: entry.username,
          note: entry.note,
          secret: '',
          password: '',
        });
      } else {
        form.resetFields();
      }
    }
  }, [open, entry, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      // 用密文加密密码
      const encrypted = await encrypt(values.password, values.secret);

      if (isEdit) {
        await updatePassword(entry!.id, {
          siteName: values.siteName,
          siteUrl: values.siteUrl || '',
          username: values.username,
          encryptedPassword: encrypted,
          note: values.note || '',
        });
        message.success('已更新');
      } else {
        await createPassword({
          siteName: values.siteName,
          siteUrl: values.siteUrl || '',
          username: values.username,
          encryptedPassword: encrypted,
          note: values.note || '',
        });
        message.success('已添加');
      }
      form.resetFields();
      onSuccess();
    } catch (e: unknown) {
      if (e instanceof Error) message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title={isEdit ? '编辑密码' : '新增密码'}
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      confirmLoading={submitting}
      okText={isEdit ? '保存' : '添加'}
      cancelText="取消"
      destroyOnClose
    >
      <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item name="siteName" label="网站名称" rules={[{ required: true, message: '请输入网站名称' }]}>
          <Input placeholder="如 GitHub、QQ" maxLength={100} />
        </Form.Item>
        <Form.Item name="siteUrl" label="网址">
          <Input placeholder="https://example.com" maxLength={500} />
        </Form.Item>
        <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input placeholder="用户名/邮箱/手机号" maxLength={200} />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password placeholder="要保存的明文密码" />
        </Form.Item>
        <Form.Item name="secret" label="密文（保险库密码）" rules={[{ required: true, message: '请输入密文' }]}
          extra="用于加密本条密码。不会上传到后端，请自行保管。不同账户可使用不同密文。">
          <Input.Password placeholder="输入密文来加密本条密码" />
        </Form.Item>
        <Form.Item name="note" label="备注">
          <Input.TextArea placeholder="额外信息（可选）" rows={2} maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default PasswordModal;
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/components/HashbookPanel/PasswordModal.tsx
git commit -m "feat: add PasswordModal for creating and editing password entries"
```

---

## 11. 前端 — ViewPasswordModal（查看密码弹窗）

### Task 12: 创建查看密码弹窗

**Files:**
- Create: `lblog-web/src/components/HashbookPanel/ViewPasswordModal.tsx`

- [ ] **Step 1: 写入查看弹窗组件**

```tsx
import { useState } from 'react';
import { Modal, Input, Button, message, Space, Typography } from 'antd';
import { CopyOutlined, EyeOutlined } from '@ant-design/icons';
import { decrypt } from '../../utils/crypto';
import type { PasswordEntry } from '../../types';

interface Props {
  entry: PasswordEntry;
  onClose: () => void;
}

const ViewPasswordModal: React.FC<Props> = ({ entry, onClose }) => {
  const [secret, setSecret] = useState('');
  const [decrypted, setDecrypted] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleDecrypt = async () => {
    if (!secret.trim()) return;
    setLoading(true);
    setError('');
    try {
      const plain = await decrypt(entry.encryptedPassword, secret);
      setDecrypted(plain);
    } catch {
      setError('解密失败，请检查密文是否正确');
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => message.success(`${label}已复制`));
  };

  return (
    <Modal
      title="查看密码"
      open
      onCancel={onClose}
      footer={null}
      destroyOnClose
    >
      <div style={{ marginTop: 8 }}>
        <div style={{ marginBottom: 12 }}>
          <div style={{ fontWeight: 600, fontSize: 16 }}>{entry.siteName}</div>
          {entry.siteUrl && (
            <a href={entry.siteUrl} target="_blank" rel="noreferrer" style={{ fontSize: 13 }}>
              {entry.siteUrl}
            </a>
          )}
        </div>

        <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span>
            <Typography.Text type="secondary">账号：</Typography.Text>
            <Typography.Text copyable={{ text: entry.username }}>{entry.username}</Typography.Text>
          </span>
          <Button size="small" icon={<CopyOutlined />} onClick={() => copyToClipboard(entry.username, '账号')}>复制</Button>
        </div>

        <div style={{ marginBottom: 8 }}>
          <Typography.Text type="secondary">密码：</Typography.Text>
          {decrypted ? (
            <Typography.Text copyable={{ text: decrypted }} code style={{ marginLeft: 4 }}>
              {decrypted}
            </Typography.Text>
          ) : (
            <span style={{ color: '#999' }}>••••••••••••</span>
          )}
        </div>

        {entry.note && (
          <div style={{ marginBottom: 12 }}>
            <Typography.Text type="secondary">备注：</Typography.Text>
            <span>{entry.note}</span>
          </div>
        )}

        {!decrypted && (
          <Space.Compact style={{ width: '100%', marginTop: 12 }}>
            <Input.Password
              placeholder="输入密文来解密"
              value={secret}
              onChange={(e) => setSecret(e.target.value)}
              onPressEnter={handleDecrypt}
            />
            <Button type="primary" icon={<EyeOutlined />} loading={loading} onClick={handleDecrypt}>
              解密
            </Button>
          </Space.Compact>
        )}

        {error && <div style={{ color: '#ff4d4f', fontSize: 12, marginTop: 4 }}>{error}</div>}

        {decrypted && (
          <Button type="primary" ghost icon={<CopyOutlined />} style={{ marginTop: 8 }} onClick={() => copyToClipboard(decrypted, '密码')}>
            复制密码
          </Button>
        )}
      </div>
    </Modal>
  );
};

export default ViewPasswordModal;
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/components/HashbookPanel/ViewPasswordModal.tsx
git commit -m "feat: add ViewPasswordModal for decrypting and viewing passwords"
```

---

## 12. 前端 — 工具箱集成

### Task 13: 集成密码本到浮动工具箱和 App

**Files:**
- Modify: `lblog-web/src/components/DrawFloatingButton.tsx`
- Modify: `lblog-web/src/App.tsx`

- [ ] **Step 1: 修改 DrawFloatingButton — 添加导入和 tools 数组**

在 `DrawFloatingButton.tsx` 中：

修改 import 添加 Key 图标：
```tsx
import { Wrench, PencilRuler, CheckSquare, Key } from 'lucide-react'
```

修改 Props 接口，添加 `onOpenHashbook`：
```tsx
interface DrawFloatingButtonProps {
  onOpenDraw: () => void
  onOpenTodo: () => void
  onOpenHashbook: () => void
  onPositionChange?: (pos: { left: number; top: number }) => void
  hidden?: boolean
}
```

修改组件解构：
```tsx
const DrawFloatingButton: React.FC<DrawFloatingButtonProps> = ({ onOpenDraw, onOpenTodo, onOpenHashbook, onPositionChange, hidden }) => {
```

在 tools 数组中追加：
```tsx
const tools: ToolItem[] = [
  { id: 'draw', label: '绘图工具', icon: <PencilRuler size={14} />, action: () => { setHover(false); onOpenDraw() } },
  { id: 'todo', label: '代办事项', icon: <CheckSquare size={14} />, action: () => { setHover(false); onOpenTodo() } },
  { id: 'hashbook', label: '密码本', icon: <Key size={14} />, action: () => { setHover(false); onOpenHashbook() } },
]
```

- [ ] **Step 2: 修改 App.tsx — 添加状态和渲染**

添加 import：
```tsx
import HashbookPanel from './components/HashbookPanel';
```

在 `AppContent` 中添加状态（在 `showTodoPanel` 行下方）：
```tsx
const [showHashbookPanel, setShowHashbookPanel] = useState(false);
```

修改 `ToolboxButton` 调用（在 JSX 中）：
```tsx
<ToolboxButton
  showDrawPage={showDrawPage}
  onOpenDraw={() => setShowDrawPage(true)}
  onOpenTodo={() => setShowTodoPanel(v => !v)}
  onOpenHashbook={() => setShowHashbookPanel(v => !v)}
  onPositionChange={setToolboxPos}
/>
```

在 TodoPanel 渲染后添加：
```tsx
{/* 密码本面板 */}
{showHashbookPanel && <HashbookPanel onClose={() => setShowHashbookPanel(false)} />}
```

修改 `ToolboxButton` 函数签名和传参：
```tsx
function ToolboxButton({ showDrawPage, onOpenDraw, onOpenTodo, onOpenHashbook, onPositionChange }: {
  showDrawPage: boolean; onOpenDraw: () => void; onOpenTodo: () => void; onOpenHashbook: () => void; onPositionChange: (pos: { left: number; top: number }) => void
}) {
  const { isAuthenticated, user } = useAuth();
  if (!isAuthenticated || !user) return null;
  if (user.role !== 'admin' && user.role !== 'author') return null;
  return <DrawFloatingButton onOpenDraw={onOpenDraw} onOpenTodo={onOpenTodo} onOpenHashbook={onOpenHashbook} onPositionChange={onPositionChange} hidden={showDrawPage} />;
}
```

- [ ] **Step 3: 编译验证前端**

```bash
cd lblog-web && npm run build
```

- [ ] **Step 4: Commit**

```bash
git add lblog-web/src/components/DrawFloatingButton.tsx
git add lblog-web/src/App.tsx
git commit -m "feat: integrate hashbook panel into floating toolbox"
```

---

## 验证清单

所有任务完成后，验证以下功能：

1. 启动后端 → `passwords` 表已创建
2. 登录博客 → 浮动工具箱中显示"密码本"入口（Key 图标）
3. 点击"密码本"→ 弹出浮动面板
4. 拖拽面板 → 关闭后位置保持
5. 点击"新增"→ 填写网站信息、密码、密文 → 添加成功
6. 列表中可搜索站点名或账号
7. 点击某条记录 → 查看弹窗显示账号和加密密码
8. 输入正确密文 → 解密成功显示明文，可复制
9. 输入错误密文 → 提示解密失败
10. 编辑条目 → 更新成功
11. 删除条目 → 软删除成功
12. 分页正常（超过 20 条时出现分页器）
