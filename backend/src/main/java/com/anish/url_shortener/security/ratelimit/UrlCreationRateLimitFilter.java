package com.anish.url_shortener.security.ratelimit;

import com.anish.url_shortener.auth.entity.User;
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
import java.util.Map;

import com.anish.url_shortener.config.AppProperties;

@Component
@RequiredArgsConstructor
public class UrlCreationRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

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

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = allowRequest(authentication, request);
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
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
        return HttpMethod.POST.matches(request.getMethod()) && "/api/v1/urls".equals(request.getRequestURI());
    }

    private boolean allowRequest(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return rateLimitService.allowAuthenticatedCreate(user.getId().toString());
        }

        return rateLimitService.allowAnonymousCreate(resolveIpAddress(request));
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}
