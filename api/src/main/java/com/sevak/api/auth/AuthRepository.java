package com.sevak.api.auth;

import com.sevak.api.auth.dto.MeResponse;
import com.sevak.api.auth.dto.UserCredentials;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

    private final JdbcClient jdbc;

    public AuthRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The API's role can't read password hashes; this function returns the one for this email. */
    public Optional<UserCredentials> findCredentials(String email) {
        return jdbc.sql("""
                        SELECT user_id, password_hash, role, status, profile_id
                        FROM fn_login_credentials(:email)
                        """).param("email", email).query(UserCredentials.class).optional();
    }

    /**
     * A new account acts as itself for the rest of registration, so it may create its own profile.
     *
     * <p>The setting lasts for this transaction only, and a @Transactional method that joins it afterwards
     * resets the caller from the request (nobody, during registration). So call nothing @Transactional after
     * this in register(); it would fail closed, not leak.
     */
    public void actAs(int userId) {
        jdbc.sql("SELECT set_config('sevak.user_id', :id, true)")
                .param("id", String.valueOf(userId))
                .query(String.class)
                .single();
    }

    public int insertUser(String email, String passwordHash, String role, String fullName, String phone) {
        Map<String, Object> params = new HashMap<>();
        params.put("email", email);
        params.put("hash", passwordHash);
        params.put("role", role);
        params.put("name", fullName);
        params.put("phone", phone);
        return jdbc.sql("""
                        INSERT INTO users (email, password_hash, role, full_name, phone)
                        VALUES (:email, :hash, :role, :name, :phone)
                        RETURNING user_id
                        """).params(params).query(Integer.class).single();
    }

    public int insertCustomer(int userId) {
        return jdbc.sql("INSERT INTO customers (user_id) VALUES (:id) RETURNING customer_id")
                .param("id", userId)
                .query(Integer.class)
                .single();
    }

    public int insertProvider(int userId, int cityId, String bio, Integer experienceYears) {
        Map<String, Object> params = new HashMap<>();
        params.put("user", userId);
        params.put("city", cityId);
        params.put("bio", bio);
        params.put("years", experienceYears);
        return jdbc.sql("""
                        INSERT INTO service_providers (user_id, city_id, bio, experience_years)
                        VALUES (:user, :city, :bio, :years)
                        RETURNING provider_id
                        """).params(params).query(Integer.class).single();
    }

    /** The API role can't read emails or phones; fn_my_contact returns the caller's own. */
    public Optional<MeResponse> findMe(int userId) {
        return jdbc.sql("""
                        SELECT u.user_id, c.email, u.full_name, c.phone, u.role
                        FROM users u CROSS JOIN fn_my_contact() c
                        WHERE u.user_id = :id
                        """).param("id", userId).query(MeResponse.class).optional();
    }
}
