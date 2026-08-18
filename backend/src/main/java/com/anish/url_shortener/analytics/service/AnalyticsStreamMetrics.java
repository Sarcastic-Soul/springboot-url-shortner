package com.anish.url_shortener.analytics.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Exports the two numbers that say whether analytics is keeping up: how long the stream is, and
 * how many messages have been delivered but not acknowledged.
 *
 * <p>Stream depth growing while pending stays flat means the consumer is too slow. Pending
 * growing means messages are being delivered and dropped. Neither was observable before —
 * the queue could back up all the way to Valkey's memory ceiling in silence.
 */
@Slf4j
@Component
@Profile("!task")
public class AnalyticsStreamMetrics implements MeterBinder {

    private static final String STREAM_KEY = AsyncAnalyticsService.STREAM_KEY;
    private static final String GROUP_NAME = "analytics-group";

    private final StringRedisTemplate redisTemplate;

    public AnalyticsStreamMetrics(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("analytics.stream.length", this, AnalyticsStreamMetrics::streamLength);
        registry.gauge("analytics.stream.pending", this, AnalyticsStreamMetrics::pendingCount);
    }

    private double streamLength() {
        return safely(() -> {
            Long size = redisTemplate.opsForStream().size(STREAM_KEY);
            return size == null ? 0d : size.doubleValue();
        });
    }

    private double pendingCount() {
        return safely(() -> {
            var summary = redisTemplate.opsForStream().pending(STREAM_KEY, GROUP_NAME);
            return summary == null ? 0d : (double) summary.getTotalPendingMessages();
        });
    }

    /** A scrape must never throw: an unreachable Valkey would take the metrics endpoint with it. */
    private double safely(java.util.function.Supplier<Double> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.debug("Could not read analytics stream metrics: {}", e.getMessage());
            return -1d;
        }
    }
}
