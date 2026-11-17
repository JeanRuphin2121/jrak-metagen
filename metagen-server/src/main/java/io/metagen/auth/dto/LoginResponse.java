package io.metagen.auth.dto;

import java.util.List;

/**
 * Response body for a successful login.
 *
 * @param token     signed JWT — include as {@code Authorization: Bearer <token>}
 * @param username  authenticated user's login name
 * @param roles     role names embedded in the token
 * @param expiresIn token lifetime in seconds
 */
public record LoginResponse(
    String       token,
    String       username,
    List<String> roles,
    long         expiresIn
) {}
