package com.sevak.api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sevak.api.meta.QueryHeaderConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Who may call what, in one place. Checks that a row belongs to the caller live in the services. */
@Configuration
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // tokens are sent in a header, never in cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/api/v1/auth/**",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/error")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/services",
                                "/api/v1/providers",
                                "/api/v1/providers/top",
                                "/api/v1/providers/*",
                                "/api/v1/providers/*/reviews",
                                "/api/v1/providers/*/slots",
                                "/api/v1/areas",
                                "/api/v1/meta/queries/*")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**", "/actuator/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/provider/**")
                        .hasRole("PROVIDER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/bookings/*/accept",
                                "/api/v1/bookings/*/decline",
                                "/api/v1/bookings/*/start",
                                "/api/v1/bookings/*/complete")
                        .hasRole("PROVIDER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/bookings",
                                "/api/v1/bookings/quote",
                                "/api/v1/bookings/*/payments",
                                "/api/v1/bookings/*/review")
                        .hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/cancel", "/api/v1/bookings/*/complaints")
                        .hasAnyRole("CUSTOMER", "PROVIDER")
                        .requestMatchers("/api/v1/me")
                        .authenticated()
                        .requestMatchers("/api/v1/me/**")
                        .hasRole("CUSTOMER")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                                (request, response, ex) -> writeUnauthorized(response))
                        .accessDeniedHandler((request, response, ex) -> writeForbidden(response)))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(roleFromToken()))
                        .authenticationEntryPoint((request, response, ex) -> writeUnauthorized(response))
                        .accessDeniedHandler((request, response, ex) -> writeForbidden(response)));
        return http.build();
    }

    /** Maps the token's "role" claim (for example CUSTOMER) to the authority ROLE_CUSTOMER. */
    private static JwtAuthenticationConverter roleFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        writeProblem(response, 401, "Unauthorized", "unauthenticated", "A valid login token is required");
    }

    private static void writeForbidden(HttpServletResponse response) throws IOException {
        writeProblem(response, 403, "Forbidden", "forbidden", "Your account can't do this");
    }

    private static void writeProblem(HttpServletResponse response, int status, String title, String type, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write("{\"type\":\"urn:sevak:error:%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                        .formatted(type, title, status, detail));
    }

    @Bean
    SecretKey jwtKey(@Value("${sevak.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("sevak.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${sevak.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
        cors.setExposedHeaders(List.of("X-Request-Id", "Retry-After", QueryHeaderConfig.HEADER));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
