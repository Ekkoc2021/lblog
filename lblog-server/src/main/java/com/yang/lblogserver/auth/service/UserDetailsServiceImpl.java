package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.Roles;
import com.yang.lblogserver.auth.domain.UserRoles;
import com.yang.lblogserver.auth.domain.Users;
import com.yang.lblogserver.auth.mapper.UserRolesMapper;
import com.yang.lblogserver.auth.mapper.UsersMapper;
import com.yang.lblogserver.auth.security.model.LoginUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UsersMapper usersMapper;
    private final UserRolesMapper userRolesMapper;
    private final RoleService roleService;

    public UserDetailsServiceImpl(UsersMapper usersMapper, UserRolesMapper userRolesMapper,
                                  RoleService roleService) {
        this.usersMapper = usersMapper;
        this.userRolesMapper = userRolesMapper;
        this.roleService = roleService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Users user = usersMapper.findByUsername(username);
        if (user == null) {
            // 懒初始化：首次部署时自动创建管理员 ekko
            if ("ekko".equals(username) && usersMapper.countAll() == 0) {
                user = createDefaultAdmin();
                if (user != null) {
                    return new LoginUser(user);
                }
            }
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }

    private Users createDefaultAdmin() {
        Users admin = new Users();
        admin.setUsername("ekko");
        admin.setPasswordHash("{noop}admin123");
        admin.setNickname("Ekko");
        admin.setEmail("ekko@example.com");
        admin.setRole("admin");
        admin.setStatus(1);
        usersMapper.insertUser(admin);

        // 分配 admin 角色（含懒初始化：首次调用自动创建全部 3 个默认角色）
        Roles adminRole = roleService.getByName("admin");
        UserRoles ur = new UserRoles();
        ur.setUserId(admin.getId());
        ur.setRoleId(adminRole.getId());
        userRolesMapper.insert(ur);

        log.info("首次启动：已自动创建管理员 ekko（初始密码 admin123，请修改）");
        return admin;
    }
}
