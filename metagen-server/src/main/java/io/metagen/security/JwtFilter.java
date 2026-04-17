package io.metagen.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that extracts and validates a JWT Bearer token on each request.
 *
 * <h2>Flow</h2>
 * <pre>
 *   HTTP Request
 *     │
 *     ├── Header: "Authorization: Bearer <token>" present?
 *     │     NO  → continue filter chain (anonymous request)
 *     │     YES ↓
 *     ├── JwtService.isValid(token)?
 *     │     NO  → 401 Unauthorized
 *     │     YES ↓
 *     ├── Extract username + roles from token
 *     ├── Create UsernamePasswordAuthenticationToken
 *     │     authorities = roles prefixed with "ROLE_"
 *     └── Set in SecurityContext → Spring Security sees user as authenticated
 * </pre>
 *
 * <p>Roles are stored in the token as plain names ({@code BUYER}, {@code ADMIN}).
 * Spring Security expects {@code ROLE_BUYER}, {@code ROLE_ADMIN} — the prefix
 * is added here. The RBAC engine strips it back when calling {@code roles(auth)}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest  request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain         chain)
        throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (!jwtService.isValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        String       username = jwtService.extractUsername(token);
        List<String> roles    = jwtService.extractRoles(token);

        var authorities = roles.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .toList();

        var authentication = new UsernamePasswordAuthenticationToken(
            username, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated '{}' with roles {}", username, roles);

        chain.doFilter(request, response);
    }
}
