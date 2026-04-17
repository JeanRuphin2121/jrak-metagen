package io.metagen.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * <h2>Security model</h2>
 * <ul>
 *   <li><strong>Stateless</strong> — no HTTP session, every request carries a JWT</li>
 *   <li><strong>CSRF disabled</strong> — not needed for stateless REST APIs</li>
 *   <li><strong>CORS</strong> — configured to allow the React frontend in dev;
 *       override {@code ALLOWED_ORIGINS} env var in production</li>
 * </ul>
 *
 * <h2>Public endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/auth/login} — obtain a JWT</li>
 *   <li>{@code GET  /actuator/health} — health check (monitoring)</li>
 *   <li>{@code GET  /swagger-ui/**}  — API docs</li>
 *   <li>{@code GET  /api-docs/**}    — OpenAPI spec</li>
 * </ul>
 *
 * <h2>Protected endpoints</h2>
 * <ul>
 *   <li>{@code /api/studio/**} — requires {@code ADMIN} role (enforced by {@code @PreAuthorize})</li>
 *   <li>{@code /api/data/**}   — requires authentication; per-entity RBAC handled by engine</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity        // enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless REST — no session, no CSRF
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers(
                    "/api/auth/**",
                    "/actuator/health",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api-docs/**"
                ).permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Add JWT filter before Spring's default username/password filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
