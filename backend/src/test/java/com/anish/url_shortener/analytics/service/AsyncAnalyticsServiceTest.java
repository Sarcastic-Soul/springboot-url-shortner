package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.url.entity.Url;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncAnalyticsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ClickEnrichmentService clickEnrichmentService;

    @Mock
    private StreamOperations<String, String, String> streamOperations;

    @InjectMocks
    private AsyncAnalyticsService asyncAnalyticsService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.<String, String>opsForStream()).thenReturn(streamOperations);
    }

    @Test
    void trackClick_EnrichesAndPublishesToRedisStream() {
        // Arrange
        Url mockUrl = new Url();
        mockUrl.setId(UUID.randomUUID());

        ClickContext context = new ClickContext("192.168.1.1", "Mozilla", "https://google.com");
        EnrichedClickContext enriched = new EnrichedClickContext("hash123", "US", "Mobile", "Chrome", "Android");

        when(clickEnrichmentService.enrich(context)).thenReturn(enriched);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(null);

        // Act
        asyncAnalyticsService.trackClick(mockUrl, context);

        // Assert
        verify(clickEnrichmentService, times(1)).enrich(context);
        verify(streamOperations, times(1)).add(any(MapRecord.class));
    }
}
