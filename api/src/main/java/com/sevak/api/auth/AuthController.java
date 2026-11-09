package com.sevak.api.auth;

import com.sevak.api.auth.dto.LoginRequest;
import com.sevak.api.auth.dto.MeResponse;
import com.sevak.api.auth.dto.RegisterRequest;
import com.sevak.api.auth.dto.TokenResponse;
import com.sevak.api.common.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "0. Account", description = "Register, log in, and see your account")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Register",
            description = "Providers start unverified and appear in search once an admin verifies them.")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Log in", description = "Returns a token to send as: Authorization: Bearer <token>")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "My account")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(CurrentUser.from(jwt).userId());
    }
}
