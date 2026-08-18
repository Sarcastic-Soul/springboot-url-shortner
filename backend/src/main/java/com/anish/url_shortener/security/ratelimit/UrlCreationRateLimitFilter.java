package com.anish.url_shortener.security.ratelimit;

import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.common.net.ClientIpResolver;
import com.anish.url_shortener.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UrlCreationRateLimitFilter extends OncePerRequestFilter {

    static final String BYPASS_HEADER = "X-RateLimit-Bypass";

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!shouldRateLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitDecision decision = decide(request);

        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remainingTokens()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of("error", "Too Many Requests. Please slow down.")
        );
    }

    private boolean shouldRateLimit(HttpServletRequest request) {
        if (!appProperties.getRateLimit().isEnabled()) {
            return false;
        }
        if (!HttpMethod.POST.matches(request.getMethod()) || !"/api/v1/urls".equals(request.getRequestURI())) {
            return false;
        }
        return !isAllowlisted(request);
    }

    /**
     * A load generator drives every request from one or two source IPs, which a per-IP limiter
     * correctly reads as abuse. Rather than raising capacity to a fictional number for benchmark
     * runs — which changes what the benchmark measures — the generator presents a shared secret
     * and is skipped. The secret is empty by default, so this does nothing in production unless
     * deliberately configured.
     */
    private boolean isAllowlisted(HttpServletRequest request) {
        String expected = appProperties.getRateLimit().getBypassSecret();
        if (expected == null || expected.isBlank()) {
            return false;
        }

        String presented = request.getHeader(BYPASS_HEADER);
        if (presented == null || presented.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8)
        );
    }

    private RateLimitDecision decide(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return rateLimitService.allowAuthenticatedCreate(user.getId().toString());
        }

        return rateLimitService.allowAnonymousCreate(clientIpResolver.resolve(request));
    }
}
