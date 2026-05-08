package com.yang.lblogserver.controller;

import com.yang.lblogserver.common.ApiResponse;
import com.yang.lblogserver.common.PageResult;
import com.yang.lblogserver.domain.Permissions;
import com.yang.lblogserver.domain.RolePermissions;
import com.yang.lblogserver.domain.Roles;
import com.yang.lblogserver.domain.UserRoles;
import com.yang.lblogserver.domain.Users;
import com.yang.lblogserver.mapper.PermissionsMapper;
import com.yang.lblogserver.mapper.RolePermissionsMapper;
import com.yang.lblogserver.mapper.RolesMapper;
import com.yang.lblogserver.mapper.UserRolesMapper;
import com.yang.lblogserver.mapper.UserTokenMapper;
import com.yang.lblogserver.mapper.UsersMapper;
import com.yang.lblogserver.vo.admin.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理端", description = "用户管理")
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UsersMapper usersMapper;
    private final UserRolesMapper userRolesMapper;
    private final RolesMapper rolesMapper;
    private final PermissionsMapper permissionsMapper;
    private final RolePermissionsMapper rolePermissionsMapper;
    private final UserTokenMapper userTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UsersMapper usersMapper,
                               UserRolesMapper userRolesMapper,
                               RolesMapper rolesMapper,
                               PermissionsMapper permissionsMapper,
                               RolePermissionsMapper rolePermissionsMapper,
                               UserTokenMapper userTokenMapper,
                               PasswordEncoder passwordEncoder) {
        this.usersMapper = usersMapper;
        this.userRolesMapper = userRolesMapper;
        this.rolesMapper = rolesMapper;
        this.permissionsMapper = permissionsMapper;
        this.rolePermissionsMapper = rolePermissionsMapper;
        this.userTokenMapper = userTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "用户列表", description = "分页查询用户列表，支持关键词搜索、状态筛选、非活跃天数筛选、角色筛选")
    @GetMapping("/users")
    public ApiResponse<PageResult<AdminUserVO>> getUserList(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer inactiveDays,
            @RequestParam(required = false) String role) {
        int offset = (page - 1) * pageSize;
        List<Users> userList = usersMapper.selectUserList(keyword, status, inactiveDays, role, offset, pageSize);
        int total = usersMapper.countUserList(keyword, status, inactiveDays, role);

        List<AdminUserVO> voList = new ArrayList<>();
        for (Users user : userList) {
            AdminUserVO vo = buildAdminUserVO(user);
            voList.add(vo);
        }

        return ApiResponse.success(PageResult.of(page, pageSize, total, voList));
    }

    @Operation(summary = "创建用户", description = "创建新用户，支持指定角色")
    @PostMapping("/users")
    public ApiResponse<?> createUser(@Valid @RequestBody CreateUserRequest request) {
        // 检查用户名是否重复
        Users existing = usersMapper.findByUsername(request.getUsername());
        if (existing != null) {
            return ApiResponse.error(400, "用户名已存在");
        }

        // 创建用户
        Users user = new Users();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        usersMapper.insertUser(user);

        // 插入角色关联
        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            List<UserRoles> roleList = request.getRoleIds().stream()
                    .map(roleId -> {
                        UserRoles ur = new UserRoles();
                        ur.setUserId(user.getId());
                        ur.setRoleId(roleId);
                        return ur;
                    })
                    .collect(Collectors.toList());
            userRolesMapper.insertBatch(roleList);
        }

        return ApiResponse.success(new IdResponse(user.getId()));
    }

    @Operation(summary = "更新用户", description = "更新用户基本信息及角色")
    @PutMapping("/users/{id}")
    public ApiResponse<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        Users user = usersMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        // 先删后插角色
        if (request.getRoleIds() != null) {
            userRolesMapper.deleteByUserId(id);
            if (!request.getRoleIds().isEmpty()) {
                List<UserRoles> roleList = request.getRoleIds().stream()
                        .map(roleId -> {
                            UserRoles ur = new UserRoles();
                            ur.setUserId(id);
                            ur.setRoleId(roleId);
                            return ur;
                        })
                        .collect(Collectors.toList());
                userRolesMapper.insertBatch(roleList);
            }
        }

        // 更新用户基本信息
        String nickname = request.getNickname() != null ? request.getNickname() : user.getNickname();
        String email = request.getEmail() != null ? request.getEmail() : user.getEmail();
        Integer status = request.getStatus() != null ? request.getStatus() : user.getStatus();
        usersMapper.updateUser(id, nickname, email, status);

        return ApiResponse.success(null);
    }

    @Operation(summary = "重置密码", description = "重置用户密码并撤销该用户所有token")
    @PutMapping("/users/{id}/reset-password")
    public ApiResponse<?> resetPassword(@PathVariable Long id,
                                        @Valid @RequestBody ResetPasswordRequest request) {
        Users user = usersMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        // 更新密码
        usersMapper.updatePassword(id, passwordEncoder.encode(request.getNewPassword()));

        // 撤销该用户所有 token
        userTokenMapper.revokeAllByUserId(id);

        return ApiResponse.success(null);
    }

    @Operation(summary = "删除用户", description = "软删除用户")
    @DeleteMapping("/users/{id}")
    public ApiResponse<?> deleteUser(@PathVariable Long id) {
        Users user = usersMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        usersMapper.softDeleteUser(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "用户详情", description = "获取单个用户详情")
    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserVO> getUser(@PathVariable Long id) {
        Users user = usersMapper.selectById(id);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        return ApiResponse.success(buildAdminUserVO(user));
    }

    @Operation(summary = "角色列表", description = "获取所有角色，含权限列表")
    @GetMapping("/roles")
    public ApiResponse<List<Roles>> getRoleList() {
        List<Roles> list = rolesMapper.selectAll();
        List<Permissions> allPerms = permissionsMapper.selectAll();
        for (Roles role : list) {
            List<RolePermissions> rps = rolePermissionsMapper.selectByRoleId(role.getId());
            if (rps != null && !rps.isEmpty()) {
                List<Long> permIds = rps.stream().map(RolePermissions::getPermissionId).collect(Collectors.toList());
                List<String> perms = allPerms.stream()
                        .filter(p -> permIds.contains(p.getId()))
                        .map(Permissions::getCode)
                        .collect(Collectors.toList());
                role.setPermissions(perms);
            } else {
                role.setPermissions(Collections.emptyList());
            }
        }
        return ApiResponse.success(list);
    }

    @Operation(summary = "权限列表", description = "获取所有权限")
    @GetMapping("/permissions")
    public ApiResponse<List<Permissions>> getPermissions() {
        return ApiResponse.success(permissionsMapper.selectAll());
    }

    @Operation(summary = "更新角色权限", description = "更新指定角色的权限列表（全量覆盖）")
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<?> updateRolePermissions(@PathVariable Long id,
                                                @RequestBody Map<String, List<String>> body) {
        List<String> codes = body.get("permissionCodes");
        if (codes == null) {
            return ApiResponse.error(400, "permissionCodes 不能为空");
        }

        // 查出所有权限 code 对应的 ID
        List<Permissions> allPerms = permissionsMapper.selectAll();
        Map<String, Long> codeToId = allPerms.stream()
                .collect(Collectors.toMap(Permissions::getCode, Permissions::getId));

        List<Long> permIds = new ArrayList<>();
        for (String code : codes) {
            Long pid = codeToId.get(code);
            if (pid != null) {
                permIds.add(pid);
            }
        }

        // 先删后插
        rolePermissionsMapper.deleteByRoleId(id);
        if (!permIds.isEmpty()) {
            List<RolePermissions> list = permIds.stream()
                    .map(permId -> {
                        RolePermissions rp = new RolePermissions();
                        rp.setRoleId(id);
                        rp.setPermissionId(permId);
                        return rp;
                    })
                    .collect(Collectors.toList());
            rolePermissionsMapper.insertBatch(list);
        }

        return ApiResponse.success(null);
    }

    /**
     * 构建管理端用户 VO（含角色信息）
     */
    private AdminUserVO buildAdminUserVO(Users user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setLoginCount(user.getLoginCount());
        vo.setCreatedAt(user.getCreatedAt());

        // 填充角色信息
        List<UserRoles> userRoleList = userRolesMapper.selectByUserId(user.getId());
        if (userRoleList != null && !userRoleList.isEmpty()) {
            List<String> roleNames = new ArrayList<>();
            List<String> roleLabels = new ArrayList<>();
            for (UserRoles ur : userRoleList) {
                Roles role = rolesMapper.selectById(ur.getRoleId());
                if (role != null) {
                    roleNames.add(role.getName());
                    roleLabels.add(role.getLabel());
                }
            }
            vo.setRoles(roleNames);
            vo.setRoleLabels(roleLabels);
        }

        return vo;
    }
}
