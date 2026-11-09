package com.sevak.api.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The logged-in caller, read from their verified token.
 *
 * @param profileId the customer_id, provider_id or admin_id, depending on the role
 */
public record CurrentUser(int userId, String role, int profileId) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                Integer.parseInt(jwt.getSubject()),
                jwt.getClaimAsString("role"),
                ((Number) jwt.getClaim("profileId")).intValue());
    }

    public boolean isCustomer() {
        return "CUSTOMER".equals(role);
    }

    public boolean isProvider() {
        return "PROVIDER".equals(role);
    }
}
