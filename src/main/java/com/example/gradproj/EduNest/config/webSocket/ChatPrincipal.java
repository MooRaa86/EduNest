package com.example.gradproj.EduNest.config.webSocket;

import java.security.Principal;

public record ChatPrincipal(
        String email,
        String fullName
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
