package com.sevak.api.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Name of the Swagger security scheme; used by @SecurityRequirement on write endpoints. */
    public static final String API_KEY_SCHEME = "apiKey";

    private static final String PROBLEM_SCHEMA = "Problem";

    @Bean
    public OpenAPI sevakOpenAPI(ObjectProvider<BuildProperties> build) {
        BuildProperties properties = build.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("SEVAK — Service Marketplace API")
                        .description("""
                                REST API for the SEVAK home-services marketplace, built on a BCNF-normalized
                                27-table PostgreSQL schema. Errors follow RFC 9457 Problem Details.
                                """)
                        .version(properties == null ? "dev" : properties.getVersion())
                        .contact(new Contact().name("Madhav").url("https://github.com/madhavthesiya")))
                // Write endpoints (POST) need the X-Api-Key header until JWT auth is added.
                // This adds an "Authorize" button in Swagger UI so "Try it out" can send the key.
                .components(new Components()
                        .addSecuritySchemes(
                                API_KEY_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Api-Key")
                                        .description("Required for POST endpoints. Locally the key is 'dev-admin-key' "
                                                + "unless SEVAK_ADMIN_KEY is set."))
                        .addSchemas(PROBLEM_SCHEMA, problemSchema()));
    }

    /** Documents the error responses every endpoint can return, in one place. */
    @Bean
    public GlobalOpenApiCustomizer standardErrorResponses() {
        return api -> api.getPaths()
                .forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                    ApiResponses responses = operation.getResponses();
                    if (path.startsWith("/api/")) {
                        addError(responses, "400", "Invalid input");
                        addError(responses, "500", "Unexpected error");
                    }
                    if (path.contains("{")) {
                        addError(responses, "404", "Not found");
                    }
                    if (method == PathItem.HttpMethod.POST) {
                        addError(responses, "401", "Missing or invalid X-Api-Key");
                    }
                }));
    }

    private static void addError(ApiResponses responses, String code, String description) {
        responses.addApiResponse(
                code,
                new ApiResponse()
                        .description(description)
                        .content(new Content()
                                .addMediaType(
                                        "application/problem+json",
                                        new MediaType()
                                                .schema(new Schema<>()
                                                        .$ref("#/components/schemas/" + PROBLEM_SCHEMA)))));
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemSchema() {
        Map<String, Schema> properties = Map.of(
                "type", new StringSchema().example("urn:sevak:error:not-found"),
                "title", new StringSchema().example("Not Found"),
                "status", new IntegerSchema().example(404),
                "detail", new StringSchema().example("Booking 9999 not found"),
                "instance", new StringSchema().example("/api/v1/bookings/9999/cancel"));
        return new ObjectSchema().properties(properties);
    }
}
