package com.anish.url_shortener.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Correlation IDs. Accepts an inbound {@code X-Request-Id}, generates one otherwise, puts it in
 * the MDC so every log line for the request carries it, and echoes it on the response.
 *
 * <p>First, deliberately: an ID that appears halfway down the filter chain cannot correlate the
 * lines emitted before it. This is also why it exists before any log shipping does — aggregated
 * logs with nothing to join on are a search engine, not a debugger.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = sanitize(request.getHeader(HEADER));

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads are pooled. Leaving the value behind would stamp the next, unrelated
            // request with this one's id.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * The inbound value is client-controlled and ends up in log output, so it is length-capped
     * and restricted to characters that cannot forge a new log line.
     */
    private String sanitize(String inbound) {
        if (inbound == null || inbound.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String trimmed = inbound.length() > MAX_LENGTH ? inbound.substring(0, MAX_LENGTH) : inbound;
        return trimmed.replaceAll("[^A-Za-z0-9._:-]", "");
    }
}
