package com.yang.lblogserver.auth.service;

import com.yang.lblogserver.auth.domain.Roles;
import com.yang.lblogserver.auth.mapper.RolesMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);
    private final RolesMapper rolesMapper;

    public RoleService(RolesMapper rolesMapper) {
        this.rolesMapper = rolesMapper;
    }

    public List<Roles> getAll() {
        List<Roles> list = rolesMapper.selectAll();
        if (list.isEmpty()) {
            insertDefaults();
            list = rolesMapper.selectAll();
        }
        return list;
    }

    public Roles getByName(String name) {
        Roles role = rolesMapper.selectByName(name);
        if (role == null) {
            insertDefaults();
            role = rolesMapper.selectByName(name);
        }
        return role;
    }

    private void insertDefaults() {
        log.info("首次初始化：创建默认角色");
        insertRole("admin", "管理员", "系统管理员，拥有所有权限", 0);
        insertRole("author", "作者", "内容创作者", 1);
        insertRole("user", "用户", "普通注册用户", 2);
    }

    private void insertRole(String name, String label, String desc, int sortOrder) {
        if (rolesMapper.selectByName(name) != null) return;
        Roles role = new Roles();
        role.setName(name);
        role.setLabel(label);
        role.setDescription(desc);
        role.setSortOrder(sortOrder);
        rolesMapper.insert(role);
    }
}
