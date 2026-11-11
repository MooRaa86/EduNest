package com.example.gradproj.EduNest.config.webSocket;

import java.security.Principal;
import java.util.UUID;

public record ChatPrincipal(
        String email,
        String fullName,
        UUID userId
) implements Principal {

    public ChatPrincipal(String email, String fullName) {
        this(email, fullName, null);
    }

    @Override
    public String getName() {
        return email;
    }
}
