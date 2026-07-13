package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.url.entity.Url;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.stream.MapRecord;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AsyncAnalyticsService {

    private final StringRedisTemplate redisTemplate;
    private final ClickEnrichmentService clickEnrichmentService;

    @Async("analyticsExecutor")
    public void trackClick(Url url, ClickContext context) {
        EnrichedClickContext enriched = clickEnrichmentService.enrich(context);

        Map<String, String> payload = new HashMap<>();
        payload.put("urlId", url.getId().toString());
        payload.put("ipAddress", context.ipAddress());
        payload.put("ipHash", enriched.ipHash());
        payload.put("country", enriched.country());
        payload.put("device", enriched.device());
        payload.put("browser", enriched.browser());
        payload.put("os", enriched.os());
        payload.put("userAgent", context.userAgent());
        payload.put("referer", context.referer());

        redisTemplate.opsForStream().add(
                MapRecord.create("analytics:click:stream", payload)
        );
    }
}
