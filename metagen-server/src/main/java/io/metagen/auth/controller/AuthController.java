package io.metagen.auth.controller;

import io.metagen.auth.dto.LoginRequest;
import io.metagen.auth.dto.LoginResponse;
import io.metagen.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication endpoints — public (no JWT required).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login and token management")
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticate with username + password and receive a JWT.
     *
     * <pre>
     * POST /api/auth/login
     * { "username": "admin", "password": "changeme" }
     *
     * → 200 OK
     * { "token": "eyJ...", "username": "admin", "roles": ["ADMIN"], "expiresIn": 86400 }
     * </pre>
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login",
        description = "Returns a JWT Bearer token. Include it as `Authorization: Bearer <token>` on all subsequent requests.")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            return authService.login(req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }
}
