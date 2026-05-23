package com.example.gradproj.EduNest.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;

@Component
@RequestScope
public class CurrentUserProvider {

    private final String email;

    public CurrentUserProvider() {
        this.email = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(auth -> auth.isAuthenticated())
                .map(auth -> auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Unauthenticated user"));
    }

    public String getEmail() {
        return email;
    }
}
