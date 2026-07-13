package com.anish.url_shortener.url.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.anish.url_shortener.url.repository.UrlRepository;
import com.anish.url_shortener.config.AppProperties;
import com.anish.url_shortener.common.util.ShortCodeGenerator;
import com.anish.url_shortener.analytics.service.AsyncAnalyticsService;
import com.anish.url_shortener.url.dto.CreateUrlRequest;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.dto.UrlResponse;
import com.anish.url_shortener.auth.entity.User;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock private UrlRepository urlRepository;
    @Mock private ShortCodeGenerator shortCodeGenerator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UrlSafetyService urlSafetyService;
    @Mock private AsyncAnalyticsService asyncAnalyticsService;
    @Mock private RedirectCacheService redirectCacheService;
    @Mock private AppProperties appProperties;

    @InjectMocks
    private UrlService urlService;

    @Test
    void testCreateUrl_Anonymous() {
        // Setup empty SecurityContext for anonymous
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        CreateUrlRequest request = new CreateUrlRequest(
                "https://google.com",
                null, null, null, null,
                LocalDateTime.now().plusDays(2),
                null, null
        );

        when(appProperties.getAnonymous()).thenReturn(new AppProperties.Anonymous());
        when(shortCodeGenerator.generate()).thenReturn("abcdef");
        when(urlRepository.existsByShortCode("abcdef")).thenReturn(false);
        when(appProperties.getBaseUrl()).thenReturn("http://localhost:8080");

        UrlResponse response = urlService.create(request);

        assertNotNull(response);
        assertEquals("abcdef", response.shortCode());
        assertEquals("http://localhost:8080/abcdef", response.shortUrl());
        verify(urlRepository, times(1)).save(any(Url.class));
    }
}
