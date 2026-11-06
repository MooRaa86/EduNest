package com.example.gradproj.EduNest.config.auth;

import java.security.Principal;
import java.util.Objects;

/** Identity of the account whose password was verified; the name remains the login email. */
public record PrincipalIdentity(Type type, long id, String email) implements Principal {
    public enum Type { USER, ADMIN }

    public PrincipalIdentity {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) {
            throw new IllegalArgumentException("Invalid principal identity");
        }
    }

    @Override
    public String getName() {
        return email;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PrincipalIdentity that && type == that.type && id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
