package com.submithub.security;

import com.submithub.domain.Role;
import com.submithub.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** 로그인한 사용자. 컨트롤러에서 @AuthenticationPrincipal 로 받는다. */
@Getter
public class AppUser implements UserDetails {

    private final Long id;
    private final String loginId;
    private final String passwordHash;
    private final String name;
    private final Role role;

    public AppUser(User user) {
        this.id = user.getId();
        this.loginId = user.getLoginId();
        this.passwordHash = user.getPasswordHash();
        this.name = user.getName();
        this.role = user.getRole();
    }

    public boolean isProfessor() {
        return role == Role.PROFESSOR;
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }
}
