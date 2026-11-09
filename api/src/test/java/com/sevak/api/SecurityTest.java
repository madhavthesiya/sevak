package com.sevak.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

/** Logging in, roles, and making sure nobody can reach someone else's data. */
class SecurityTest extends IntegrationTest {

    private static final String DEMO_PASSWORD = "Demo@1234";

    @Autowired
    private JwtEncoder realEncoder;

    private ResultActions json(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return json("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static String register(String email, String password, String role, String extra) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"fullName\":\"New Person\",\"role\":\"%s\"%s}"
                .formatted(email, password, role, extra);
    }

    private String sign(JwtEncoder encoder, Instant expires) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("14")
                .issuedAt(expires.minusSeconds(60))
                .expiresAt(expires)
                .claim("role", "ADMIN")
                .claim("profileId", 1)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void aDemoAccountCanLogIn() throws Exception {
        String body = login("aarav.sharma@gmail.com", DEMO_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("customer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        mvc.perform(get("/api/v1/me").header(AUTH, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aarav Sharma"));
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailLookIdentical() throws Exception {
        login("aarav.sharma@gmail.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        login("nobody@example.com", DEMO_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void anInactiveAccountCannotLogIn() throws Exception {
        login("arjun.desai@gmail.com", DEMO_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void registeringGivesATokenAndStoresOnlyAHash() throws Exception {
        json("/api/v1/auth/register", register("New.Person@Example.com", "a-long-password", "customer", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists());

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'new.person@example.com'", String.class);
        assertThat(stored).startsWith("$2a$").doesNotContain("a-long-password");
        login("new.person@example.com", "a-long-password").andExpect(status().isOk());
    }

    @Test
    void aNewProviderStartsUnverifiedAndHidden() throws Exception {
        json("/api/v1/auth/register", register("pro@example.com", "a-long-password", "provider", ",\"cityId\":1"))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                        "SELECT sp.verification_status FROM service_providers sp JOIN users u ON u.user_id = sp.user_id"
                                + " WHERE u.email = 'pro@example.com'",
                        String.class))
                .isEqualTo("pending");
    }

    @Test
    void nobodyCanRegisterAsAnAdmin() throws Exception {
        json("/api/v1/auth/register", register("evil@example.com", "a-long-password", "admin", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDuplicateEmailIsRejected() throws Exception {
        json("/api/v1/auth/register", register("AARAV.SHARMA@gmail.com", "a-long-password", "customer", ""))
                .andExpect(status().isConflict());
    }

    @Test
    void aShortPasswordIsRejected() throws Exception {
        json("/api/v1/auth/register", register("short@example.com", "short", "customer", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aProviderMustChooseACity() throws Exception {
        json("/api/v1/auth/register", register("nocity@example.com", "a-long-password", "provider", ""))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/api/v1/me",
                "/api/v1/me/bookings",
                "/api/v1/provider/jobs",
                "/api/v1/admin/revenue",
                "/actuator/metrics"
            })
    void protectedEndpointsNeedALogin(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:sevak:error:unauthenticated"));
    }

    @Test
    void writesNeedALogin() throws Exception {
        mvc.perform(post("/api/v1/bookings/10/accept")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRolesAreRefused() throws Exception {
        mvc.perform(get("/api/v1/me/bookings").header(AUTH, provider(1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:sevak:error:forbidden"));
        mvc.perform(get("/api/v1/provider/jobs").header(AUTH, customer(1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/revenue").header(AUTH, customer(1))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/revenue").header(AUTH, provider(1))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bookings/10/accept").header(AUTH, customer(7)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").header(AUTH, customer(1))).andExpect(status().isForbidden());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        String token = sign(realEncoder, Instant.now().minusSeconds(3600));

        mvc.perform(get("/api/v1/admin/revenue").header(AUTH, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedTokenIsRefused() throws Exception {
        byte[] otherKey = "a-completely-different-secret-key-value".getBytes(StandardCharsets.UTF_8);
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        String token = sign(forger, Instant.now().plusSeconds(3600));

        mvc.perform(get("/api/v1/admin/revenue").header(AUTH, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageInsteadOfATokenIsRefused() throws Exception {
        mvc.perform(get("/api/v1/me").header(AUTH, "Bearer not-a-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherCustomersBookingLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(get("/api/v1/me/bookings/1").header(AUTH, customer(2))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/10/cancel")
                        .header(AUTH, customer(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Not mine\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/10/payments")
                        .header(AUTH, customer(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"UPI\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherProvidersJobLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(post("/api/v1/bookings/10/accept").header(AUTH, provider(2)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/bookings/10/decline").header(AUTH, provider(2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aProviderSeesOnlyTheirOwnJobs() throws Exception {
        mvc.perform(get("/api/v1/provider/jobs?status=completed").header(AUTH, provider(4)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].bookingId").value(9));
    }

    @Test
    void theFrontendOriginIsAllowed() throws Exception {
        mvc.perform(options("/api/v1/services")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherOriginsAreRefused() throws Exception {
        mvc.perform(options("/api/v1/services")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
