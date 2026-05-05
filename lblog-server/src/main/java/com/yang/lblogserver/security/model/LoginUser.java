package com.yang.lblogserver.security.model;

import com.yang.lblogserver.domain.Users;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class LoginUser implements UserDetails {
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String role;
    private String password;
    private boolean enabled;

    public LoginUser(Users user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.avatar = user.getAvatar();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.password = user.getPasswordHash();
        this.enabled = user.getStatus() != null && user.getStatus() == 1;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }

    public Long getUserId() { return userId; }

    public String getNickname() { return nickname; }

    public String getAvatar() { return avatar; }

    public String getEmail() { return email; }

    public String getRole() { return role; }
}
