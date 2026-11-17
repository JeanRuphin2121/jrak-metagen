package io.metagen.auth.service;

import io.metagen.auth.dto.LoginRequest;
import io.metagen.auth.dto.LoginResponse;
import io.metagen.core.domain.MetaRole;
import io.metagen.core.domain.PlatformUser;
import io.metagen.core.repository.MetaRoleRepository;
import io.metagen.core.repository.PlatformUserRepository;
import io.metagen.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles authentication — login and initial admin seeding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlatformUserRepository userRepository;
    private final MetaRoleRepository     roleRepository;
    private final PasswordEncoder        passwordEncoder;
    private final JwtService             jwtService;

    @Value("${metagen.jwt.expiration-ms:86400000}")
    private long expirationMs;

    // ── Login ─────────────────────────────────────────────────────────

    /**
     * Validates credentials and returns a signed JWT.
     *
     * @throws IllegalArgumentException if credentials are invalid
     */
    public LoginResponse login(LoginRequest req) {
        PlatformUser user = userRepository.findByUsernameWithRoles(req.username())
            .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is disabled");
        }
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String       token = jwtService.generate(user.getUsername(), user.roleNames());
        List<String> roles = user.roleNames();

        log.info("User '{}' logged in with roles {}", user.getUsername(), roles);

        return new LoginResponse(token, user.getUsername(), roles, expirationMs / 1000);
    }

    // ── Seed admin ────────────────────────────────────────────────────

    /**
     * Creates the initial admin user on first startup if no users exist.
     * Called by {@code DataInitializer} after Flyway migrations run.
     *
     * @param rawPassword plain-text password (will be BCrypt-hashed)
     */
    @Transactional
    public void seedAdminIfAbsent(String rawPassword) {
        if (userRepository.existsByUsername("admin")) return;

        MetaRole adminRole = roleRepository.findByName("ADMIN")
            .orElseThrow(() -> new IllegalStateException(
                "ADMIN role not found — check Flyway V1 migration"));

        PlatformUser admin = PlatformUser.builder()
            .username("admin")
            .email("admin@metagen.local")
            .password(passwordEncoder.encode(rawPassword))
            .enabled(true)
            .build();
        admin.getRoles().add(adminRole);

        userRepository.save(admin);
        log.info("Seeded initial admin user");
    }
}
