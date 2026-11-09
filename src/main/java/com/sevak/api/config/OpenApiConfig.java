package com.sevak.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Name of the Swagger security scheme; used by @SecurityRequirement on write endpoints. */
    public static final String API_KEY_SCHEME = "apiKey";

    @Bean
    public OpenAPI sevakOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SEVAK — Service Marketplace API")
                        .description("""
                                RESTful API for the SEVAK Service Marketplace Database.
                                Built on a BCNF-normalized 27-table PostgreSQL schema featuring
                                advanced SQL queries (multi-table JOINs, Window Functions, Stored Procedures).
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Madhav").url("https://github.com/madhavthesiya")))
                // Write endpoints (POST) need the X-Api-Key header until JWT auth arrives in Phase 4.
                // This adds an "Authorize" button in Swagger UI so "Try it out" can send the key.
                .components(new Components()
                        .addSecuritySchemes(
                                API_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Api-Key")
                                        .description("Required for POST endpoints. Locally the key is 'dev-admin-key' "
                                                + "unless SEVAK_ADMIN_KEY is set.")));
    }
}
