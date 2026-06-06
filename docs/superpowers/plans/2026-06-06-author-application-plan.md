# 作者申请渠道实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为普通用户提供申请成为作者的渠道，管理员审核通过后自动升级角色

**Architecture:** 新增 `author_applications` 表 + 用户端 API（提交/查看/补充） + 管理端 API（分页列表/审核），前端新增申请 Modal 和管理端审核页面

**Tech Stack:** Spring Boot + MyBatis (XML mapper) + React + TypeScript + Ant Design

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `auth/domain/AuthorApplication.java` | 申请表实体 |
| 新建 | `auth/vo/ApplicationRequest.java` | 提交/更新申请请求体 |
| 新建 | `auth/vo/ApplicationVO.java` | 申请记录响应体 |
| 新建 | `auth/vo/ApplicationReviewRequest.java` | 审核请求体 |
| 新建 | `auth/mapper/AuthorApplicationMapper.java` | MyBatis Mapper 接口 |
| 新建 | `resources/.../auth/mapper/AuthorApplicationMapper.xml` | MyBatis SQL |
| 新建 | `auth/service/AuthorApplicationService.java` | 业务逻辑 + 审核升权 |
| 新建 | `auth/controller/user/UserApplicationController.java` | 用户端 API `/api/v1/user/application` |
| 新建 | `auth/controller/admin/AdminApplicationController.java` | 管理端 API `/api/v1/admin/applications` |
| 修改 | `lblog-web/src/types/index.ts` | 新增 AuthorApplication 类型 |
| 修改 | `lblog-web/src/services/api.ts` | 新增 5 个 API 函数 |
| 新建 | `lblog-web/src/components/AuthorApplicationModal.tsx` | 申请/状态展示 Modal |
| 修改 | `lblog-web/src/layouts/MainLayout.tsx` | 创作中心入口逻辑 |
| 新建 | `lblog-web/src/pages/admin/ApplicationManage.tsx` | 管理端审核页面 |
| 修改 | `lblog-web/src/App.tsx` | 新增路由 + 导入 |
| 修改 | `lblog-web/src/pages/admin/AdminDashboard.tsx` | 新增导航卡片 |

---

### Task 1: 后端 Domain + VO

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/domain/AuthorApplication.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationRequest.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationVO.java`
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationReviewRequest.java`

- [ ] **Step 1: 创建 AuthorApplication 实体**

```java
package com.yang.lblogserver.auth.domain;

import lombok.Data;
import java.util.Date;

@Data
public class AuthorApplication {
    private Long id;
    private Long userId;
    private String reason;
    private Integer status;     // 0=待审核 1=通过 2=拒绝 3=需补充
    private String feedback;
    private Long reviewedBy;
    private Date reviewedAt;
    private Date createdAt;
    private Date updatedAt;
}
```

- [ ] **Step 2: 创建 ApplicationRequest**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "作者申请请求")
public class ApplicationRequest {

    @NotBlank(message = "申请理由不能为空")
    @Schema(description = "申请理由/自我介绍")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
```

- [ ] **Step 3: 创建 ApplicationVO**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;

@Schema(description = "申请记录视图")
public class ApplicationVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String reason;
    private Integer status;
    private String feedback;
    private Long reviewedBy;
    private Date reviewedAt;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: 创建 ApplicationReviewRequest**

```java
package com.yang.lblogserver.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "审核请求")
public class ApplicationReviewRequest {

    @NotNull(message = "审核状态不能为空")
    @Schema(description = "审核结果：1=通过 2=拒绝 3=需补充")
    private Integer status;

    @Schema(description = "审核反馈（拒绝或需补充时必填）")
    private String feedback;

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
```

- [ ] **Step 5: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/domain/AuthorApplication.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationRequest.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationVO.java \
        lblog-server/src/main/java/com/yang/lblogserver/auth/vo/ApplicationReviewRequest.java
git commit -m "feat: 新增作者申请 domain + VO 类"
```

---

### Task 2: 后端 Mapper + XML

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/AuthorApplicationMapper.java`
- Create: `lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/AuthorApplicationMapper.xml`

- [ ] **Step 1: 创建 Mapper 接口**

```java
package com.yang.lblogserver.auth.mapper;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorApplicationMapper {

    int insert(AuthorApplication app);

    AuthorApplication selectByUserId(@Param("userId") Long userId);

    AuthorApplication selectById(@Param("id") Long id);

    int updateReason(@Param("id") Long id, @Param("reason") String reason, @Param("status") Integer status);

    int updateReview(@Param("id") Long id, @Param("status") Integer status,
                     @Param("feedback") String feedback, @Param("reviewedBy") Long reviewedBy);

    List<ApplicationVO> selectApplicationList(@Param("status") Integer status,
                                              @Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    int countApplicationList(@Param("status") Integer status,
                             @Param("keyword") String keyword);
}
```

- [ ] **Step 2: 创建 Mapper XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.yang.lblogserver.auth.mapper.AuthorApplicationMapper">

    <resultMap id="BaseResultMap" type="com.yang.lblogserver.auth.domain.AuthorApplication">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="reason" column="reason"/>
        <result property="status" column="status"/>
        <result property="feedback" column="feedback"/>
        <result property="reviewedBy" column="reviewed_by"/>
        <result property="reviewedAt" column="reviewed_at"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <resultMap id="ApplicationVOMap" type="com.yang.lblogserver.auth.vo.ApplicationVO">
        <id property="id" column="id"/>
        <result property="userId" column="user_id"/>
        <result property="username" column="username"/>
        <result property="nickname" column="nickname"/>
        <result property="reason" column="reason"/>
        <result property="status" column="status"/>
        <result property="feedback" column="feedback"/>
        <result property="reviewedBy" column="reviewed_by"/>
        <result property="reviewedAt" column="reviewed_at"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO author_applications (user_id, reason, status, feedback, reviewed_by, reviewed_at, created_at, updated_at)
        VALUES (#{userId}, #{reason}, #{status}, #{feedback}, #{reviewedBy}, #{reviewedAt}, NOW(), NOW())
    </insert>

    <select id="selectByUserId" resultMap="BaseResultMap">
        SELECT id, user_id, reason, status, feedback, reviewed_by, reviewed_at, created_at, updated_at
        FROM author_applications
        WHERE user_id = #{userId}
        LIMIT 1
    </select>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT id, user_id, reason, status, feedback, reviewed_by, reviewed_at, created_at, updated_at
        FROM author_applications
        WHERE id = #{id}
        LIMIT 1
    </select>

    <update id="updateReason">
        UPDATE author_applications
        SET reason = #{reason}, status = #{status}, updated_at = NOW()
        WHERE id = #{id}
    </update>

    <update id="updateReview">
        UPDATE author_applications
        SET status = #{status}, feedback = #{feedback}, reviewed_by = #{reviewedBy},
            reviewed_at = NOW(), updated_at = NOW()
        WHERE id = #{id}
    </update>

    <sql id="List_Column">
        a.id, a.user_id, u.username, u.nickname, a.reason, a.status,
        a.feedback, a.reviewed_by, a.reviewed_at, a.created_at, a.updated_at
    </sql>

    <select id="selectApplicationList" resultMap="ApplicationVOMap">
        SELECT <include refid="List_Column"/>
        FROM author_applications a
        JOIN users u ON u.id = a.user_id
        WHERE 1=1
        <if test="status != null">
            AND a.status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                 OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        ORDER BY a.created_at DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countApplicationList" resultType="int">
        SELECT COUNT(*)
        FROM author_applications a
        JOIN users u ON u.id = a.user_id
        WHERE 1=1
        <if test="status != null">
            AND a.status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                 OR u.nickname LIKE CONCAT('%', #{keyword}, '%'))
        </if>
    </select>

</mapper>
```

- [ ] **Step 3: 为 UsersMapper 添加 updateRole 方法**

在 `UsersMapper.java` 接口末尾添加：

```java
int updateRole(@Param("id") Long id, @Param("role") String role);
```

在 `UsersMapper.xml` 末尾（`</mapper>` 之前）添加：

```xml
<update id="updateRole">
    UPDATE users SET role = #{role} WHERE id = #{id}
</update>
```

> 原因：`updateUser` 的 XML 无条件 SET `nickname`, `email`, `status`，传 null 会覆盖原值。需要专门的 updateRole 方法。

- [ ] **Step 4: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/AuthorApplicationMapper.java \
        lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/AuthorApplicationMapper.xml \
        lblog-server/src/main/java/com/yang/lblogserver/auth/mapper/UsersMapper.java \
        lblog-server/src/main/resources/com/yang/lblogserver/auth/mapper/UsersMapper.xml
git commit -m "feat: 新增 AuthorApplicationMapper + UsersMapper.updateRole"
```

---

### Task 3: 后端 Service

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/service/AuthorApplicationService.java`

- [ ] **Step 1: 创建 AuthorApplicationService**

```java
package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.domain.Roles;
import com.yang.lblogserver.auth.mapper.AuthorApplicationMapper;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.auth.mapper.UserRolesMapper;
import com.yang.lblogserver.auth.domain.UserRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorApplicationService {

    private final AuthorApplicationMapper applicationMapper;
    private final UsersMapper usersMapper;
    private final UserRolesMapper userRolesMapper;
    private final RoleService roleService;

    public AuthorApplicationService(AuthorApplicationMapper applicationMapper,
                                     UsersMapper usersMapper,
                                     UserRolesMapper userRolesMapper,
                                     RoleService roleService) {
        this.applicationMapper = applicationMapper;
        this.usersMapper = usersMapper;
        this.userRolesMapper = userRolesMapper;
        this.roleService = roleService;
    }

    /** 提交申请（首次） */
    public AuthorApplication submit(Long userId, String reason) {
        AuthorApplication existing = applicationMapper.selectByUserId(userId);
        if (existing != null) {
            if (existing.getStatus() == 1) {
                throw new IllegalStateException("您已是作者，无需重复申请");
            }
            if (existing.getStatus() == 0) {
                throw new IllegalStateException("您已有待审核的申请，请耐心等待");
            }
        }

        AuthorApplication app = new AuthorApplication();
        app.setUserId(userId);
        app.setReason(reason);
        app.setStatus(0);
        applicationMapper.insert(app);
        return app;
    }

    /** 查自己的申请 */
    public AuthorApplication getByUserId(Long userId) {
        return applicationMapper.selectByUserId(userId);
    }

    /** 补充材料后重新提交 */
    public void resubmit(Long userId, String reason) {
        AuthorApplication existing = applicationMapper.selectByUserId(userId);
        if (existing == null) {
            throw new IllegalStateException("未找到申请记录");
        }
        if (existing.getStatus() != 2 && existing.getStatus() != 3) {
            throw new IllegalStateException("当前状态不允许重新提交");
        }
        applicationMapper.updateReason(existing.getId(), reason, 0);
    }

    /** 管理端分页列表 */
    public PageResult<ApplicationVO> getApplicationList(int page, int pageSize,
                                                         Integer status, String keyword) {
        int offset = (page - 1) * pageSize;
        List<ApplicationVO> list = applicationMapper.selectApplicationList(status, keyword, offset, pageSize);
        int total = applicationMapper.countApplicationList(status, keyword);
        return PageResult.of(page, pageSize, total, list);
    }

    /** 审核 */
    @Transactional
    public void review(Long applicationId, Integer status, String feedback, Long reviewerId) {
        AuthorApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (app.getStatus() != 0) {
            throw new IllegalStateException("该申请已审核过");
        }

        applicationMapper.updateReview(applicationId, status, feedback, reviewerId);

        // 通过时自动升级为作者
        if (status == 1) {
            usersMapper.updateRole(app.getUserId(), "author");

            // 同步 user_roles 表
            Roles authorRole = roleService.getByName("author");
            if (authorRole != null) {
                userRolesMapper.deleteByUserId(app.getUserId());
                UserRoles ur = new UserRoles();
                ur.setUserId(app.getUserId());
                ur.setRoleId(authorRole.getId());
                userRolesMapper.insertBatch(List.of(ur));
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/service/AuthorApplicationService.java
git commit -m "feat: 新增 AuthorApplicationService 业务逻辑"
```

---

### Task 4: 后端用户端 Controller

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/controller/user/UserApplicationController.java`

- [ ] **Step 1: 创建 UserApplicationController**

```java
package com.yang.lblogserver.auth.controller.user;

import com.yang.lblogserver.auth.domain.AuthorApplication;
import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.AuthorApplicationService;
import com.yang.lblogserver.auth.vo.ApplicationRequest;
import com.yang.lblogserver.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户", description = "作者申请")
@Validated
@RestController
@RequestMapping("/api/v1/user")
@PreAuthorize("isAuthenticated()")
public class UserApplicationController {

    private final AuthorApplicationService applicationService;

    public UserApplicationController(AuthorApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "提交作者申请")
    @PostMapping("/application")
    public ApiResponse<AuthorApplication> submit(@Valid @RequestBody ApplicationRequest request) {
        Long userId = getCurrentUserId();
        try {
            AuthorApplication app = applicationService.submit(userId, request.getReason());
            return ApiResponse.success(app);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @Operation(summary = "查询申请状态")
    @GetMapping("/application")
    public ApiResponse<AuthorApplication> getStatus() {
        Long userId = getCurrentUserId();
        AuthorApplication app = applicationService.getByUserId(userId);
        return ApiResponse.success(app);
    }

    @Operation(summary = "补充材料后重新提交")
    @PutMapping("/application")
    public ApiResponse<?> resubmit(@Valid @RequestBody ApplicationRequest request) {
        Long userId = getCurrentUserId();
        try {
            applicationService.resubmit(userId, request.getReason());
            return ApiResponse.success(null);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/controller/user/UserApplicationController.java
git commit -m "feat: 新增用户端作者申请 API"
```

---

### Task 5: 后端管理端 Controller

**Files:**
- Create: `lblog-server/src/main/java/com/yang/lblogserver/auth/controller/admin/AdminApplicationController.java`

- [ ] **Step 1: 创建 AdminApplicationController**

```java
package com.yang.lblogserver.auth.controller.admin;

import com.yang.lblogserver.auth.security.model.LoginUser;
import com.yang.lblogserver.auth.service.AuthorApplicationService;
import com.yang.lblogserver.auth.vo.ApplicationReviewRequest;
import com.yang.lblogserver.auth.vo.ApplicationVO;
import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
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

@Tag(name = "管理端", description = "作者申请审核")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApplicationController {

    private final AuthorApplicationService applicationService;

    public AdminApplicationController(AuthorApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUserId();
        }
        return null;
    }

    @Operation(summary = "作者申请列表")
    @GetMapping("/applications")
    public ApiResponse<PageResult<ApplicationVO>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(applicationService.getApplicationList(page, pageSize, status, keyword));
    }

    @Operation(summary = "审核作者申请", description = "通过/拒绝/要求补充")
    @PutMapping("/applications/{id}")
    public ApiResponse<?> review(@PathVariable Long id,
                                  @Valid @RequestBody ApplicationReviewRequest request) {
        Long reviewerId = getCurrentUserId();
        try {
            applicationService.review(id, request.getStatus(), request.getFeedback(), reviewerId);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add lblog-server/src/main/java/com/yang/lblogserver/auth/controller/admin/AdminApplicationController.java
git commit -m "feat: 新增管理端作者申请审核 API"
```

---

### Task 6: 前端 Types + API 函数

**Files:**
- Modify: `lblog-web/src/types/index.ts`
- Modify: `lblog-web/src/services/api.ts`

- [ ] **Step 1: 在 types/index.ts 末尾新增类型**

```ts
// ---- 作者申请 ----

export interface AuthorApplication {
  id: number;
  userId: number;
  username: string;
  nickname: string;
  reason: string;
  status: number;  // 0=待审核 1=通过 2=拒绝 3=需补充
  feedback: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
```

- [ ] **Step 2: 在 api.ts 末尾新增 API 函数**

在 `services/api.ts` 末尾（`export async function updateTokenConfig` 之后）新增：

```ts
// ---- 作者申请 ----

export async function submitApplication(reason: string): Promise<ApiResponse<AuthorApplication>> {
  return request<AuthorApplication>('/api/v1/user/application', {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export async function getMyApplication(): Promise<ApiResponse<AuthorApplication | null>> {
  return request<AuthorApplication | null>('/api/v1/user/application');
}

export async function resubmitApplication(reason: string): Promise<ApiResponse<null>> {
  return request<null>('/api/v1/user/application', {
    method: 'PUT',
    body: JSON.stringify({ reason }),
  });
}

export async function getApplications(params?: {
  page?: number;
  pageSize?: number;
  status?: number;
  keyword?: string;
}): Promise<ApiResponse<PageResult<AuthorApplication>>> {
  return request<PageResult<AuthorApplication>>(`/api/v1/admin/applications${buildQuery(params as Record<string, string | number | undefined>)}`);
}

export async function reviewApplication(id: number, status: number, feedback?: string): Promise<ApiResponse<null>> {
  return request<null>(`/api/v1/admin/applications/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ status, feedback }),
  });
}
```

- [ ] **Step 3: Commit**

```bash
git add lblog-web/src/types/index.ts lblog-web/src/services/api.ts
git commit -m "feat: 新增作者申请前端 types + API 函数"
```

---

### Task 7: 前端 AuthorApplicationModal 组件

**Files:**
- Create: `lblog-web/src/components/AuthorApplicationModal.tsx`

- [ ] **Step 1: 创建 AuthorApplicationModal**

```tsx
import { useState, useEffect } from 'react';
import { Modal, Input, Button, message, Descriptions, Tag } from 'antd';
import { getMyApplication, submitApplication, resubmitApplication } from '../services/api';
import type { AuthorApplication } from '../types';
import { useNavigate } from 'react-router-dom';

const { TextArea } = Input;

const statusMap: Record<number, { label: string; color: string }> = {
  0: { label: '审核中', color: 'processing' },
  1: { label: '已通过', color: 'success' },
  2: { label: '已拒绝', color: 'error' },
  3: { label: '需补充材料', color: 'warning' },
};

interface Props {
  open: boolean;
  onClose: () => void;
}

const AuthorApplicationModal: React.FC<Props> = ({ open, onClose }) => {
  const navigate = useNavigate();
  const [app, setApp] = useState<AuthorApplication | null>(null);
  const [reason, setReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setLoading(true);
      getMyApplication()
        .then(res => {
          const data = res.data;
          setApp(data);
          if (data && (data.status === 2 || data.status === 3)) {
            setReason(data.reason);
          }
        })
        .catch(() => message.error('获取申请状态失败'))
        .finally(() => setLoading(false));
    }
  }, [open]);

  const handleSubmit = async () => {
    if (!reason.trim()) {
      message.warning('请填写申请理由');
      return;
    }
    setSubmitting(true);
    try {
      if (app && (app.status === 2 || app.status === 3)) {
        await resubmitApplication(reason.trim());
        message.success('已重新提交申请');
      } else {
        await submitApplication(reason.trim());
        message.success('申请已提交');
      }
      // 重新获取状态
      const res = await getMyApplication();
      setApp(res.data);
    } catch (e: any) {
      message.error(e.message || '操作失败');
    } finally {
      setSubmitting(false);
    }
  };

  const goToAuthorCenter = () => {
    onClose();
    navigate('/author/posts');
  };

  const renderContent = () => {
    if (loading) return <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>;

    // 无申请记录 → 填写表单
    if (!app) {
      return (
        <div>
          <div style={{ marginBottom: 12, color: 'var(--color-text-secondary)', fontSize: 13 }}>
            请简要介绍您的技术背景和创作方向，管理员审核通过后即可开始创作。
          </div>
          <TextArea
            rows={5}
            placeholder="请填写申请理由..."
            value={reason}
            onChange={e => setReason(e.target.value)}
            maxLength={500}
            showCount
          />
          <Button
            type="primary"
            block
            style={{ marginTop: 16 }}
            loading={submitting}
            onClick={handleSubmit}
          >
            提交申请
          </Button>
        </div>
      );
    }

    // 已通过 → 引导进入创作中心
    if (app.status === 1) {
      return (
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <Tag color="success" style={{ fontSize: 14, padding: '4px 16px' }}>已通过</Tag>
          <div style={{ marginTop: 16, color: 'var(--color-text-secondary)' }}>
            您已成为作者，可以进入创作中心开始写作
          </div>
          <Button type="primary" style={{ marginTop: 16 }} onClick={goToAuthorCenter}>
            进入创作中心
          </Button>
        </div>
      );
    }

    // 待审核
    if (app.status === 0) {
      return (
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <Tag color="processing" style={{ fontSize: 14, padding: '4px 16px' }}>审核中</Tag>
          <Descriptions column={1} style={{ marginTop: 16 }} size="small">
            <Descriptions.Item label="申请理由">{app.reason}</Descriptions.Item>
            <Descriptions.Item label="提交时间">{new Date(app.createdAt).toLocaleString()}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 12, color: 'var(--color-text-tertiary)', fontSize: 12 }}>
            请耐心等待管理员审核
          </div>
        </div>
      );
    }

    // 拒绝(2) 或 需补充(3) → 可编辑重新提交
    return (
      <div>
        {app.feedback && (
          <div style={{
            background: app.status === 3 ? '#fffbe6' : '#fff2f0',
            border: `1px solid ${app.status === 3 ? '#ffe58f' : '#ffccc7'}`,
            borderRadius: 6,
            padding: '8px 12px',
            marginBottom: 12,
            fontSize: 13,
            color: app.status === 3 ? '#ad6800' : '#a8071a',
          }}>
            <strong>{app.status === 3 ? '管理员要求补充：' : '审核未通过，原因：'}</strong>
            {app.feedback}
          </div>
        )}
        <TextArea
          rows={5}
          placeholder="请修改申请理由..."
          value={reason}
          onChange={e => setReason(e.target.value)}
          maxLength={500}
          showCount
        />
        <Button
          type="primary"
          block
          style={{ marginTop: 16 }}
          loading={submitting}
          onClick={handleSubmit}
        >
          重新提交
        </Button>
      </div>
    );
  };

  const statusTag = app ? statusMap[app.status] : null;

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          作者申请
          {statusTag && <Tag color={statusTag.color}>{statusTag.label}</Tag>}
        </div>
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width={480}
      destroyOnClose
    >
      {renderContent()}
    </Modal>
  );
};

export default AuthorApplicationModal;
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/components/AuthorApplicationModal.tsx
git commit -m "feat: 新增作者申请 AuthorApplicationModal 组件"
```

---

### Task 8: 前端创作中心入口改造

**Files:**
- Modify: `lblog-web/src/layouts/MainLayout.tsx`

- [ ] **Step 1: 修改 MainLayout.tsx**

在文件顶部添加 import：

```tsx
import AuthorApplicationModal from '../components/AuthorApplicationModal';
```

在组件内部添加状态（放在其他 `useState` 附近）：

```tsx
const [appModalVisible, setAppModalVisible] = useState(false);
```

修改第 295-302 行创作中心按钮的 onClick：

当前代码（约在第 295 行）：
```tsx
onClick={() => {
  setLoginModalVisible(false);
  if (user?.role === 'user') {
    message.info('申请成为作者后才能使用创作中心');
    return;
  }
  navigate('/author/posts');
}}
```

改为：
```tsx
onClick={() => {
  setLoginModalVisible(false);
  if (user?.role === 'user') {
    setAppModalVisible(true);
    return;
  }
  navigate('/author/posts');
}}
```

在组件 JSX 末尾（LoginModal 之后、最外层 return 之前）添加：

```tsx
<AuthorApplicationModal
  open={appModalVisible}
  onClose={() => setAppModalVisible(false)}
/>
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/layouts/MainLayout.tsx
git commit -m "feat: 创作中心入口接入作者申请 Modal"
```

---

### Task 9: 前端管理端审核页面

**Files:**
- Create: `lblog-web/src/pages/admin/ApplicationManage.tsx`

- [ ] **Step 1: 创建 ApplicationManage**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { Card, Select, Input, Button, Table, Tag, message, Modal, Typography } from 'antd';
import { ReloadOutlined, CheckOutlined, CloseOutlined, ExclamationCircleOutlined } from '@ant-design/icons';
import { getApplications, reviewApplication } from '../../services/api';
import type { AuthorApplication } from '../../types';

const { TextArea } = Input;
const { Text, Title } = Typography;

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: '0', label: '待审核' },
  { value: '1', label: '已通过' },
  { value: '2', label: '已拒绝' },
  { value: '3', label: '需补充' },
];

const statusMap: Record<number, { label: string; color: string }> = {
  0: { label: '待审核', color: 'processing' },
  1: { label: '已通过', color: 'success' },
  2: { label: '已拒绝', color: 'error' },
  3: { label: '需补充', color: 'warning' },
};

const ApplicationManage: React.FC = () => {
  const [data, setData] = useState<AuthorApplication[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');

  // 审核弹窗
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewTarget, setReviewTarget] = useState<AuthorApplication | null>(null);
  const [reviewAction, setReviewAction] = useState<number | null>(null); // 1=通过 2=拒绝 3=需补充
  const [reviewFeedback, setReviewFeedback] = useState('');
  const [reviewing, setReviewing] = useState(false);

  const fetchData = useCallback(() => {
    setLoading(true);
    getApplications({ page, pageSize, status, keyword: keyword || undefined })
      .then(res => {
        setData(res.data.list);
        setTotal(res.data.total);
      })
      .catch(() => message.error('获取申请列表失败'))
      .finally(() => setLoading(false));
  }, [page, pageSize, status, keyword]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleReview = (record: AuthorApplication, action: number) => {
    setReviewTarget(record);
    setReviewAction(action);
    setReviewFeedback('');
    setReviewModalOpen(true);
  };

  const confirmReview = async () => {
    if (!reviewTarget || reviewAction === null) return;
    if ((reviewAction === 2 || reviewAction === 3) && !reviewFeedback.trim()) {
      message.warning('请填写审核意见');
      return;
    }
    setReviewing(true);
    try {
      await reviewApplication(reviewTarget.id, reviewAction, reviewFeedback.trim() || undefined);
      message.success('审核完成');
      setReviewModalOpen(false);
      fetchData();
    } catch (e: any) {
      message.error(e.message || '审核失败');
    } finally {
      setReviewing(false);
    }
  };

  const actionLabel = reviewAction === 1 ? '通过申请' : reviewAction === 2 ? '拒绝申请' : '要求补充材料';

  const columns = [
    {
      title: '申请人',
      key: 'user',
      width: 160,
      render: (_: any, r: AuthorApplication) => (
        <div>
          <div style={{ fontWeight: 500 }}>{r.nickname}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>{r.username}</Text>
        </div>
      ),
    },
    {
      title: '申请理由',
      dataIndex: 'reason',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (s: number) => {
        const info = statusMap[s];
        return <Tag color={info?.color}>{info?.label || s}</Tag>;
      },
    },
    {
      title: '申请时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (t: string) => new Date(t).toLocaleString(),
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_: any, r: AuthorApplication) => {
        if (r.status !== 0) {
          return r.feedback ? (
            <Text type="secondary" style={{ fontSize: 12 }}>反馈：{r.feedback}</Text>
          ) : (
            <Text type="secondary">--</Text>
          );
        }
        return (
          <div style={{ display: 'flex', gap: 4 }}>
            <Button type="link" size="small" icon={<CheckOutlined />} onClick={() => handleReview(r, 1)}>
              通过
            </Button>
            <Button type="link" size="small" danger icon={<CloseOutlined />} onClick={() => handleReview(r, 2)}>
              拒绝
            </Button>
            <Button type="link" size="small" icon={<ExclamationCircleOutlined />} onClick={() => handleReview(r, 3)}>
              需补充
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Title level={4} style={{ marginBottom: 16 }}>作者申请审核</Title>

      <Card style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          <Select
            style={{ width: 120 }}
            placeholder="状态"
            allowClear
            value={status}
            onChange={(v) => { setStatus(v); setPage(1); }}
            options={statusOptions}
          />
          <Input.Search
            style={{ width: 240 }}
            placeholder="搜索用户名/昵称"
            allowClear
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onSearch={() => { setPage(1); fetchData(); }}
          />
          <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
        </div>
      </Card>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
      />

      <Modal
        title={actionLabel}
        open={reviewModalOpen}
        onOk={confirmReview}
        onCancel={() => setReviewModalOpen(false)}
        confirmLoading={reviewing}
        okText="确认"
        cancelText="取消"
      >
        {(reviewAction === 2 || reviewAction === 3) && (
          <div style={{ marginTop: 8 }}>
            <div style={{ marginBottom: 6, color: 'var(--color-text-secondary)', fontSize: 13 }}>
              {reviewAction === 3 ? '请说明需要补充什么材料：' : '请说明拒绝原因：'}
            </div>
            <TextArea
              rows={4}
              value={reviewFeedback}
              onChange={e => setReviewFeedback(e.target.value)}
              placeholder="填写审核意见..."
            />
          </div>
        )}
        {reviewAction === 1 && (
          <div style={{ padding: '12px 0', color: 'var(--color-text-secondary)' }}>
            确认通过该用户的作者申请？通过后用户将获得作者权限。
          </div>
        )}
      </Modal>
    </div>
  );
};

export default ApplicationManage;
```

- [ ] **Step 2: Commit**

```bash
git add lblog-web/src/pages/admin/ApplicationManage.tsx
git commit -m "feat: 新增管理端作者申请审核页面"
```

---

### Task 10: 前端路由 + 导航入口

**Files:**
- Modify: `lblog-web/src/App.tsx`
- Modify: `lblog-web/src/pages/admin/AdminDashboard.tsx`

- [ ] **Step 1: App.tsx 新增 import 和路由**

在 import 区域添加（紧接在 `import SessionManage from './pages/admin/SessionManage';` 之后）：

```tsx
import ApplicationManage from './pages/admin/ApplicationManage';
```

在路由区域（`/admin/sessions` 之后、`<Route path="/author"` 之前）添加：

```tsx
              <Route path="/admin/applications" element={<ApplicationManage />} />
```

- [ ] **Step 2: AdminDashboard 新增导航卡片**

在 `AdminDashboard.tsx` 的 import 区域添加图标：

```tsx
import { SettingOutlined, PictureOutlined, UserOutlined, FileTextOutlined, FolderOutlined, TagsOutlined, BookOutlined, MessageOutlined, RobotOutlined, SafetyCertificateOutlined, FormOutlined } from '@ant-design/icons';
```

在 `features` 数组末尾（`sessions` 对象之后、`];` 闭括号之前）添加：

```tsx
  {
    key: 'applications',
    title: '作者申请',
    description: '审核用户作者申请，通过后自动升级为作者角色',
    icon: <FormOutlined style={{ fontSize: 32, color: '#fa8c16' }} />,
    path: '/admin/applications',
  },
```

- [ ] **Step 3: Commit**

```bash
git add lblog-web/src/App.tsx lblog-web/src/pages/admin/AdminDashboard.tsx
git commit -m "feat: 新增作者申请路由 + 管理端导航入口"
```

---

### Task 11: 集成验证

- [ ] **Step 1: 编译后端**

```bash
cd lblog-server && mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 2: 编译前端**

```bash
cd lblog-web && npx tsc --noEmit
```

预期：无类型错误

- [ ] **Step 3: 验证完整流程（手动测试要点）**

1. 用 `user` 角色登录，点击"创作中心" → 弹出申请 Modal → 填写理由 → 提交 → 切换到"审核中"状态
2. 关闭 Modal 再次点击"创作中心" → 显示审核中状态
3. 用 `admin` 角色登录，进入"社区管理" → 点击"作者申请" → 看到申请列表
4. 点击"需补充" → 填 feedback → 确定 → 用户端重新打开 Modal 应看到补充要求
5. 用户重新编辑理由并提交 → 状态回到待审核
6. 管理员点击"通过" → 用户端显示已通过 + 进入创作中心按钮
7. 点击"进入创作中心" → 成功导航到 `/author/posts`
