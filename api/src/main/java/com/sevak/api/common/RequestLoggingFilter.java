package com.sevak.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives each request an X-Request-Id, adds it to every log line, and logs one line per request.
 * The query string is left out of the log because it can contain personal data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String supplied = req.getHeader(HEADER);
        // Only reuse a caller's ID if it is safe to write into logs.
        String requestId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        res.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} in {} ms", req.getMethod(), req.getRequestURI(), res.getStatus(), millis);
            MDC.remove("requestId");
        }
    }
}
