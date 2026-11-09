package com.sevak.api.auth;

import com.sevak.api.auth.dto.LoginRequest;
import com.sevak.api.auth.dto.MeResponse;
import com.sevak.api.auth.dto.RegisterRequest;
import com.sevak.api.auth.dto.TokenResponse;
import com.sevak.api.auth.dto.UserCredentials;
import com.sevak.api.common.ConflictException;
import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PgErrors;
import com.sevak.api.common.UnauthorizedException;
import com.sevak.api.common.UnprocessableException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String BAD_LOGIN = "Invalid email or password";

    private final AuthRepository repo;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(AuthRepository repo, PasswordEncoder passwords, TokenService tokens) {
        this.repo = repo;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        boolean isProvider = "provider".equals(request.role());
        if (isProvider && request.cityId() == null) {
            throw new IllegalArgumentException("cityId: is required for a provider");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            int userId = repo.insertUser(
                    email, passwords.encode(request.password()), request.role(), request.fullName(), request.phone());
            int profileId = isProvider
                    ? repo.insertProvider(userId, request.cityId(), request.bio(), request.experienceYears())
                    : repo.insertCustomer(userId);
            return tokenFor(userId, request.role(), profileId);
        } catch (DataAccessException ex) {
            if (PgErrors.UNIQUE_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new ConflictException("An account with this email already exists");
            }
            if (PgErrors.FOREIGN_KEY_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new UnprocessableException("City " + request.cityId() + " does not exist");
            }
            throw ex;
        }
    }

    /** An unknown email and a wrong password look identical, and take similar time, to the caller. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        UserCredentials user = repo.findCredentials(request.email().trim()).orElse(null);
        boolean passwordMatches = passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash());
        if (user == null || !passwordMatches || user.profileId() == null) {
            throw new UnauthorizedException(BAD_LOGIN);
        }
        if (!"active".equals(user.status())) {
            throw new UnauthorizedException("This account is not active");
        }
        return tokenFor(user.userId(), user.role(), user.profileId());
    }

    @Transactional(readOnly = true)
    public MeResponse me(int userId) {
        return repo.findMe(userId).orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private TokenResponse tokenFor(int userId, String role, int profileId) {
        return new TokenResponse(tokens.issue(userId, role, profileId), "Bearer", tokens.validitySeconds(), role);
    }
}
