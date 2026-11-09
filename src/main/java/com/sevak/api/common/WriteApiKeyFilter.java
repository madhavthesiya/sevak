package com.sevak.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Temporary protection for write endpoints until JWT authentication is added. */
@Component
public class WriteApiKeyFilter extends OncePerRequestFilter {

    @Value("${sevak.admin-key}")
    private String adminKey;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        boolean isWrite = !"GET".equals(req.getMethod()) && !"OPTIONS".equals(req.getMethod());
        if (isWrite && !adminKey.equals(req.getHeader("X-Api-Key"))) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(req, res);
    }
}
