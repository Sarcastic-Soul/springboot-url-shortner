package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains the click stream into Postgres. Every replica runs one; the consumer group makes them
 * competing consumers rather than duplicates.
 */
@Slf4j
@Service
@Profile("!task")
@RequiredArgsConstructor
public class ClickStreamConsumer {

    private static final String STREAM_KEY = AsyncAnalyticsService.STREAM_KEY;
    private static final String GROUP_NAME = "analytics-group";

    private final StringRedisTemplate redisTemplate;
    private final ClickBatchWriter clickBatchWriter;
    private final AppProperties appProperties;

    private String consumerName;
    private volatile boolean groupReady;

    @PostConstruct
    void init() {
        this.consumerName = resolveConsumerName();
        // Best effort. Valkey may not be up yet, and the application booting is more important
        // than the group existing this instant -- the poll loop retries.
        ensureGroup();
        log.info("Analytics stream consumer '{}' initialised", consumerName);
    }

    /**
     * A pod's identity, not a fresh UUID per process.
     *
     * <p>With a random name, everything a pod had in flight when it died was orphaned in the
     * pending list under a consumer that would never return, and no later pod could recognise
     * the entries as its own. A stable name plus the reclaim pass below closes that hole.
     */
    private String resolveConsumerName() {
        String hostname = System.getenv("HOSTNAME");
        return (hostname == null || hostname.isBlank())
                ? "instance-" + UUID.randomUUID()
                : hostname;
    }

    /**
     * Creates the consumer group, tolerating only BUSYGROUP.
     *
     * <p>This used to run on every poll inside {@code catch (Exception e) {}}, which swallowed
     * genuine failures — an unreachable Valkey looked exactly like a group that already existed.
     */
    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            groupReady = true;
        } catch (RuntimeException e) {
            if (isBusyGroup(e)) {
                groupReady = true;
                return;
            }
            log.warn("Could not create consumer group {}: {}", GROUP_NAME, e.getMessage());
        }
    }

    private boolean isBusyGroup(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }

    @Scheduled(fixedDelayString = "${app.analytics.poll-interval-ms:5000}")
    public void consumeClicks() {
        ensureGroup();
        if (!groupReady) {
            return;
        }

        try {
            drain(ReadOffset.lastConsumed());
        } catch (RuntimeException e) {
            log.error("Error consuming analytics stream", e);
        }
    }

    /**
     * Reclaims entries stranded in the pending list by a pod that never came back, then
     * processes them. Without this, a restart during a batch lost those clicks permanently.
     */
    @Scheduled(fixedDelayString = "${app.analytics.reclaim-interval-ms:60000}")
    public void reclaimOrphaned() {
        if (!groupReady) {
            return;
        }

        Duration minIdle = Duration.ofMillis(appProperties.getAnalytics().getReclaimIdleMs());

        try {
            PendingMessages pending = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), appProperties.getAnalytics().getBatchSize());

            List<RecordId> stale = new ArrayList<>();
            for (PendingMessage message : pending) {
                if (message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) > 0) {
                    stale.add(message.getId());
                }
            }

            if (stale.isEmpty()) {
                return;
            }

            redisTemplate.opsForStream().claim(
                    STREAM_KEY,
                    GROUP_NAME,
                    consumerName,
                    org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
                            .minIdle(minIdle)
                            .ids(stale)
            );

            log.info("Reclaimed {} orphaned analytics messages", stale.size());

            // ReadOffset "0" returns this consumer's own pending entries -- the ones just
            // claimed -- rather than new arrivals.
            drain(ReadOffset.from("0"));
        } catch (RuntimeException e) {
            log.error("Error reclaiming orphaned analytics messages", e);
        }
    }

    private void drain(ReadOffset offset) {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                Consumer.from(GROUP_NAME, consumerName),
                StreamReadOptions.empty().count(appProperties.getAnalytics().getBatchSize()).block(Duration.ofMillis(100)),
                StreamOffset.create(STREAM_KEY, offset)
        );

        if (messages == null || messages.isEmpty()) {
            return;
        }

        List<ClickBatchWriter.ClickRow> rows = new ArrayList<>(messages.size());
        List<RecordId> processed = new ArrayList<>(messages.size());

        for (MapRecord<String, Object, Object> message : messages) {
            ClickBatchWriter.ClickRow row = parse(message);
            if (row != null) {
                rows.add(row);
            }
            // Acknowledge unparseable messages too. Leaving them in the pending list means
            // reclaiming and re-failing on them forever.
            processed.add(message.getId());
        }

        int written = clickBatchWriter.write(rows);

        // Acknowledge only after the transaction commits. A crash before this point leaves the
        // entries pending, and the reclaim pass picks them up: at-least-once, never zero.
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, processed.toArray(new RecordId[0]));

        // No XDEL. The producer's MAXLEN caps the stream, which works whether or not the
        // consumer is keeping up.
        log.debug("Persisted {} of {} analytics messages", written, messages.size());
    }

    private ClickBatchWriter.ClickRow parse(MapRecord<String, Object, Object> message) {
        Map<Object, Object> payload = message.getValue();
        try {
            return new ClickBatchWriter.ClickRow(
                    UUID.fromString(string(payload, "urlId")),
                    LocalDateTime.now(),
                    string(payload, "ipAddress"),
                    string(payload, "ipHash"),
                    string(payload, "device"),
                    string(payload, "browser"),
                    string(payload, "os"),
                    string(payload, "userAgent"),
                    string(payload, "referer")
            );
        } catch (RuntimeException e) {
            log.error("Failed to parse analytics stream message {}", message.getId(), e);
            return null;
        }
    }

    private static String string(Map<Object, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isEmpty() ? null : text;
    }
}
