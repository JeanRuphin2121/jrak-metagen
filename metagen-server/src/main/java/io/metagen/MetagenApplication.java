package io.metagen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Metagen Platform — entry point.
 *
 * <p>Starts the Spring Boot application which assembles:
 * <ul>
 *   <li>{@code metagen-core}   — JPA domain model + repositories</li>
 *   <li>{@code metagen-engine} — Dynamic CRUD runtime + workflow engine</li>
 *   <li>{@code metagen-server} — REST API, auth, Studio endpoints</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>
 *   docker compose -f docker/docker-compose.dev.yml up -d
 *   ./mvnw spring-boot:run -pl metagen-server
 * </pre>
 *
 * The Swagger UI is available at <a href="http://localhost:8080/swagger-ui.html">
 * http://localhost:8080/swagger-ui.html</a> once the server is running.
 * </p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MetagenApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetagenApplication.class, args);
    }
}
