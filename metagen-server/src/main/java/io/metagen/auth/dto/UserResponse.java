package io.metagen.auth.dto;

import io.metagen.core.domain.PlatformUser;

import java.util.List;
import java.util.UUID;

/**
 * Read-only representation of a {@link PlatformUser}.
 * Password is never included.
 */
public record UserResponse(
    UUID         id,
    String       username,
    String       email,
    boolean      enabled,
    List<String> roles
) {
    public static UserResponse from(PlatformUser u) {
        return new UserResponse(
            u.getId(),
            u.getUsername(),
            u.getEmail(),
            u.isEnabled(),
            u.roleNames()
        );
    }
}
