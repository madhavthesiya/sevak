package com.sevak.api.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits requests per IP address: strictly for login and creating bookings, loosely for the rest.
 * Limits are kept in memory, so with several app instances each one counts separately.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final long strictPerMinute;
    private final long generalPerMinute;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(
            @Value("${sevak.rate-limit.strict-per-minute}") long strictPerMinute,
            @Value("${sevak.rate-limit.general-per-minute}") long generalPerMinute) {
        this.strictPerMinute = strictPerMinute;
        this.generalPerMinute = generalPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        boolean strict = isStrict(req);
        long perMinute = strict ? strictPerMinute : generalPerMinute;
        Bucket bucket = buckets.get(
                (strict ? "strict:" : "general:") + req.getRemoteAddr(),
                key -> Bucket.builder()
                        .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, WINDOW))
                        .build());
        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
        res.setContentType("application/problem+json");
        res.getWriter()
                .write("{\"type\":\"urn:sevak:error:rate-limited\",\"title\":\"Too Many Requests\","
                        + "\"status\":429,\"detail\":\"Too many requests. Please try again in a minute.\"}");
    }

    private static boolean isStrict(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri.startsWith("/api/v1/auth/") || ("POST".equals(req.getMethod()) && uri.equals("/api/v1/bookings"));
    }
}
