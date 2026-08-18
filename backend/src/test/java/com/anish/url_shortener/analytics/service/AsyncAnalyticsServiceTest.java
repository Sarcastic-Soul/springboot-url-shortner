package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncAnalyticsServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ClickEnrichmentService clickEnrichmentService;

    private AsyncAnalyticsService service() {
        return new AsyncAnalyticsService(redisTemplate, clickEnrichmentService, new AppProperties());
    }

    @Test
    void trackClick_enrichesAndPublishesToTheStream() {
        ClickContext context = new ClickContext("192.168.1.1", "Mozilla", "https://google.com");
        when(clickEnrichmentService.enrich(context))
                .thenReturn(new EnrichedClickContext("hash123", "Mobile", "Chrome", "Android"));

        service().trackClick(UUID.randomUUID(), context);

        verify(clickEnrichmentService, times(1)).enrich(context);
        verify(redisTemplate, times(1)).execute(any(RedisCallback.class));
    }

    /** A cache entry written before urlId existed has none. That must not raise. */
    @Test
    void trackClick_withoutAUrlIdIsANoOp() {
        service().trackClick(null, new ClickContext("192.168.1.1", "Mozilla", null));

        verifyNoInteractions(clickEnrichmentService);
        verifyNoInteractions(redisTemplate);
    }
}
