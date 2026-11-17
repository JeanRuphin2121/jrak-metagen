package io.metagen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for creating a new platform user.
 *
 * @param username plain username (unique)
 * @param email    contact email
 * @param password plain-text password — will be BCrypt-hashed before storage
 * @param roles    list of role names to assign (e.g. ["USER", "MANAGER"])
 */
public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
    List<String> roles
) {}
