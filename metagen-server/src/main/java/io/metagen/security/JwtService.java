package io.metagen.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Handles JWT token generation and validation.
 *
 * <h2>Token structure</h2>
 * <pre>
 * {
 *   "sub":   "alice",                  // username
 *   "roles": ["BUYER", "USER"],        // role names (without ROLE_ prefix)
 *   "iat":   1713300000,
 *   "exp":   1713386400               // iat + expirationMs
 * }
 * </pre>
 *
 * <p>The RBAC engine reads {@code roles} to resolve permissions.
 * Adding a role to a token automatically grants all permissions
 * configured for that role in {@code meta_permission}.</p>
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long      expirationMs;

    public JwtService(
        @Value("${metagen.jwt.secret}") String secret,
        @Value("${metagen.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.secretKey    = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // ── Generate ─────────────────────────────────────────────────────

    /**
     * Generates a signed JWT for the given user.
     *
     * @param username login name → {@code sub} claim
     * @param roles    list of role names (without {@code ROLE_} prefix)
     * @return compact JWT string
     */
    public String generate(String username, List<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(username)
            .claim("roles", roles)
            .issuedAt(new Date(now))
            .expiration(new Date(now + expirationMs))
            .signWith(secretKey)
            .compact();
    }

    // ── Validate & parse ─────────────────────────────────────────────

    /**
     * Parses and validates the token.
     *
     * @return claims if valid
     * @throws JwtException if the token is expired, tampered, or malformed
     */
    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /** Returns true if the token can be parsed without error. */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    /** Extracts the username ({@code sub} claim) from a valid token. */
    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    /** Extracts the roles list from a valid token. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parse(token).get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
