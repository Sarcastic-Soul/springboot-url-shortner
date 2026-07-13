package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickStreamConsumer {

    private static final String STREAM_KEY = "analytics:click:stream";
    private static final String GROUP_NAME = "analytics-group";
    private final String consumerName = "instance-" + UUID.randomUUID().toString();

    private final StringRedisTemplate redisTemplate;
    private final UrlClickRepository urlClickRepository;
    private final UrlRepository urlRepository;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void consumeClicks() {
        try {
            // Ensure consumer group exists
            try {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            } catch (Exception e) {
                // Group already exists, ignore
            }

            // Read batch from stream
            List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                    Consumer.from(GROUP_NAME, consumerName),
                    StreamReadOptions.empty().count(1000).block(Duration.ofMillis(100)),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (messages == null || messages.isEmpty()) {
                return;
            }

            List<UrlClick> clicksToSave = new ArrayList<>();
            List<RecordId> processedIds = new ArrayList<>();

            for (MapRecord<String, Object, Object> message : messages) {
                Map<Object, Object> payload = message.getValue();
                
                try {
                    UUID urlId = UUID.fromString((String) payload.get("urlId"));
                    Url url = urlRepository.findById(urlId).orElse(null);
                    
                    if (url != null) {
                        UrlClick click = UrlClick.builder()
                                .url(url)
                                .ipAddress((String) payload.get("ipAddress"))
                                .ipHash((String) payload.get("ipHash"))
                                .country((String) payload.get("country"))
                                .device((String) payload.get("device"))
                                .browser((String) payload.get("browser"))
                                .os((String) payload.get("os"))
                                .userAgent((String) payload.get("userAgent"))
                                .referer((String) payload.get("referer"))
                                .build();
                        clicksToSave.add(click);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse analytics stream message {}", message.getId(), e);
                }
                
                processedIds.add(message.getId());
            }

            if (!clicksToSave.isEmpty()) {
                urlClickRepository.saveAll(clicksToSave);
            }

            if (!processedIds.isEmpty()) {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, processedIds.toArray(new RecordId[0]));
                redisTemplate.opsForStream().delete(STREAM_KEY, processedIds.toArray(new RecordId[0]));
            }

        } catch (Exception e) {
            log.error("Error consuming analytics stream", e);
        }
    }
}
