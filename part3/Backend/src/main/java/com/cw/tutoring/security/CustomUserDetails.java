package com.cw.tutoring.security;

import com.cw.tutoring.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final Integer id;
    private final String email;
    private final String password;
    private final String roleName;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.roleName = user.getRole() != null ? user.getRole().getFullName() : "student";
    }

    public Integer getId() {
        return this.id;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String springRole = "ROLE_" + roleName.toUpperCase();
        return List.of(new SimpleGrantedAuthority(springRole));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
