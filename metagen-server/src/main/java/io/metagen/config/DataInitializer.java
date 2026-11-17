package io.metagen.config;

import io.metagen.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once on startup (after Flyway migrations) to seed required data.
 *
 * <p>Creates the initial admin user if none exists.
 * Uses the {@code METAGEN_ADMIN_PASSWORD} environment variable
 * (default: {@code changeme} — override in production).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final AuthService authService;

    @Value("${spring.security.user.password:changeme}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if ("changeme".equals(adminPassword)) {
            log.warn("The admin account uses the default password 'changeme' — "
                   + "set METAGEN_ADMIN_PASSWORD before exposing this server.");
        }
        authService.seedAdminIfAbsent(adminPassword);
        log.info("Metagen Platform ready — Studio API at /api/studio, Swagger at /swagger-ui.html");
    }
}
