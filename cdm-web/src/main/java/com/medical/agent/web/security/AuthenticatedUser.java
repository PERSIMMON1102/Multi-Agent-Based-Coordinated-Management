package com.medical.agent.web.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;

public record AuthenticatedUser(Long id, String username, String role) implements Principal {

    @Override
    public String getName() {
        return username;
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isDoctor() {
        return "doctor".equals(role);
    }

    public boolean isPatient() {
        return "patient".equals(role);
    }

    public SimpleGrantedAuthority authority() {
        return new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
    }
}
