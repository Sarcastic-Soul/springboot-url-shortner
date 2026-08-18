package com.anish.url_shortener.security.ratelimit;

import com.anish.url_shortener.common.net.ClientIpResolver;
import com.anish.url_shortener.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UrlCreationRateLimitFilterTest {

    private AppProperties appProperties;
    private RateLimitService rateLimitService;
    private UrlCreationRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        rateLimitService = mock(RateLimitService.class);
        filter = new UrlCreationRateLimitFilter(
                rateLimitService,
                new ObjectMapper(),
                appProperties,
                new ClientIpResolver(appProperties)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest createRequest(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/urls");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    /**
     * The defect this exists to prevent: with the header trusted blindly, a fresh
     * X-Forwarded-For per request produced a fresh bucket per request, so the quota was never
     * reached no matter how many requests one caller sent.
     */
    @Test
    void spoofedForwardedForDoesNotEarnAFreshBucket() throws Exception {
        Set<String> bucketsUsed = new HashSet<>();
        when(rateLimitService.allowAnonymousCreate(anyString())).thenAnswer(invocation -> {
            bucketsUsed.add(invocation.getArgument(0));
            return RateLimitDecision.allow(1);
        });

        for (int i = 0; i < 50; i++) {
            filter.doFilter(
                    createRequest("10.0.0.7", "203.0.113." + i),
                    new MockHttpServletResponse(),
                    new MockFilterChain()
            );
        }

        assertEquals(Set.of("10.0.0.7"), bucketsUsed,
                "every request came from the same peer, so it must map to a single bucket");
    }

    @Test
    void refusalCarriesRetryAfter() throws Exception {
        when(rateLimitService.allowAnonymousCreate(anyString()))
                .thenReturn(new RateLimitDecision(false, 0, 3));

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(createRequest("10.0.0.7", null), response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("3", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too Many Requests"));
    }

    @Test
    void allowlistedGeneratorSkipsTheLimiter() throws Exception {
        appProperties.getRateLimit().setBypassSecret("s3cret");

        MockHttpServletRequest request = createRequest("10.0.0.7", null);
        request.addHeader(UrlCreationRateLimitFilter.BYPASS_HEADER, "s3cret");

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(rateLimitService, org.mockito.Mockito.never()).allowAnonymousCreate(anyString());
    }

    @Test
    void wrongBypassSecretIsStillRateLimited() throws Exception {
        appProperties.getRateLimit().setBypassSecret("s3cret");
        when(rateLimitService.allowAnonymousCreate(anyString())).thenReturn(RateLimitDecision.allow(1));

        MockHttpServletRequest request = createRequest("10.0.0.7", null);
        request.addHeader(UrlCreationRateLimitFilter.BYPASS_HEADER, "guess");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verify(rateLimitService).allowAnonymousCreate("10.0.0.7");
    }
}
