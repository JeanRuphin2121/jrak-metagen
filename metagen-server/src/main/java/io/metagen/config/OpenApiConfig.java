package io.metagen.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI / Swagger UI documentation.
 *
 * <p>The generated spec is available at {@code /api-docs} (JSON)
 * and the interactive UI at {@code /swagger-ui.html}.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI metaGenOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Metagen Platform API")
                .description("""
                    Open-source metadata-driven low-code platform.

                    **Studio API** — manage entities, fields, workflows, roles, and views.

                    **Runtime API** — dynamically generated CRUD endpoints for every configured entity.

                    Authenticate via `/api/auth/login` to get a Bearer token, then click **Authorize**.
                    """)
                .version("0.1.0")
                .contact(new Contact()
                    .name("Metagen Contributors")
                    .url("https://github.com/your-org/metagen"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .name(BEARER_SCHEME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the JWT token obtained from /api/auth/login")));
    }
}
