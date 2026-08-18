package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAnalyticsService {

    public static final String STREAM_KEY = "analytics:click:stream";

    private final StringRedisTemplate redisTemplate;
    private final ClickEnrichmentService clickEnrichmentService;
    private final AppProperties appProperties;

    /**
     * Takes a url id rather than a {@code Url} entity: the cache-hit redirect path has an id and
     * no entity, and enqueuing must not become a reason to load one.
     */
    @Async("analyticsExecutor")
    public void trackClick(UUID urlId, ClickContext context) {
        if (urlId == null) {
            return;
        }

        EnrichedClickContext enriched = clickEnrichmentService.enrich(context);

        Map<String, String> payload = new HashMap<>();
        payload.put("urlId", urlId.toString());
        put(payload, "ipAddress", context.ipAddress());
        put(payload, "ipHash", enriched.ipHash());
        put(payload, "device", enriched.device());
        put(payload, "browser", enriched.browser());
        put(payload, "os", enriched.os());
        put(payload, "userAgent", context.userAgent());
        put(payload, "referer", context.referer());

        try {
            add(MapRecord.create(STREAM_KEY, payload));
        } catch (RuntimeException e) {
            // A click is worth less than a redirect. Never fail the redirect over analytics.
            log.warn("Failed to enqueue click for url {}", urlId, e);
        }
    }

    /**
     * Trims on write with {@code MAXLEN ~}, which is what caps the stream.
     *
     * <p>The consumer used to XDEL every entry it acknowledged, which is both a second round
     * trip and no protection at all: if the consumer falls behind or dies, nothing trims and the
     * stream grows until Valkey hits its memory ceiling. An approximate MAXLEN on the producer
     * bounds it regardless of consumer health, and trims whole radix nodes rather than entries.
     */
    private void add(MapRecord<String, String, String> record) {
        long maxLen = appProperties.getAnalytics().getStreamMaxLength();
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<RecordId>) connection ->
                connection.streamCommands().xAdd(
                        record.serialize(
                                redisTemplate.getStringSerializer(),
                                redisTemplate.getStringSerializer(),
                                redisTemplate.getStringSerializer()
                        ),
                        org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions
                                .maxlen(maxLen)
                                .approximateTrimming(true)
                )
        );
    }

    private static void put(Map<String, String> payload, String key, String value) {
        // Redis stream fields cannot hold null, and a missing Referer is normal.
        payload.put(key, value == null ? "" : value);
    }
}
