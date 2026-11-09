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

    public Optional<UserCredentials> findCredentials(String email) {
        return jdbc.sql("""
                        SELECT u.user_id, u.password_hash, u.role, u.status,
                               COALESCE(c.customer_id, sp.provider_id, a.admin_id) AS profile_id
                        FROM users u
                        LEFT JOIN customers c          ON c.user_id = u.user_id
                        LEFT JOIN service_providers sp ON sp.user_id = u.user_id
                        LEFT JOIN admins a             ON a.user_id = u.user_id
                        WHERE LOWER(u.email) = LOWER(:email)
                        """).param("email", email).query(UserCredentials.class).optional();
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

    public Optional<MeResponse> findMe(int userId) {
        return jdbc.sql("SELECT user_id, email, full_name, phone, role FROM users WHERE user_id = :id")
                .param("id", userId)
                .query(MeResponse.class)
                .optional();
    }
}
