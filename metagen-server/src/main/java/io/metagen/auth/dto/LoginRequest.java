package io.metagen.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/auth/login}.
 *
 * @param username login name
 * @param password plain-text password (compared against BCrypt hash)
 */
public record LoginRequest(
    @NotBlank String username,
    @NotBlank String password
) {}
