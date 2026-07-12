package com.anish.url_shortener.url.service;

import com.anish.url_shortener.config.AppProperties;
import com.anish.url_shortener.url.dto.CachedRedirectEntry;
import com.anish.url_shortener.url.entity.Url;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectCacheService {

    private static final String CACHE_KEY_PREFIX = "redirect:code:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public Optional<CachedRedirectEntry> get(String shortCode) {
        String value = redisTemplate.opsForValue().get(key(shortCode));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, CachedRedirectEntry.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize redirect cache for {}", shortCode, e);
            redisTemplate.delete(key(shortCode));
            return Optional.empty();
        }
    }

    public void putForAnonymousRedirect(Url url) {
        if (!isCacheableAnonymousRedirect(url)) {
            evict(url.getShortCode());
            return;
        }

        CachedRedirectEntry entry = new CachedRedirectEntry(url.getOriginalUrl(), url.getExpiresAt());

        try {
            String value = objectMapper.writeValueAsString(entry);
            Duration ttl = resolveTtl(url.getExpiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                evict(url.getShortCode());
                return;
            }
            redisTemplate.opsForValue().set(key(url.getShortCode()), value, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize redirect cache for {}", url.getShortCode(), e);
        }
    }

    public void evict(String shortCode) {
        redisTemplate.delete(key(shortCode));
    }

    private boolean isCacheableAnonymousRedirect(Url url) {
        return url.getUser() == null
                && Boolean.TRUE.equals(url.getIsActive())
                && (url.getPasswordHash() == null || url.getPasswordHash().isBlank())
                && url.getMaxClicks() == null;
    }

    private Duration resolveTtl(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            return Duration.ofSeconds(appProperties.getRedirectCache().getTtlSeconds());
        }

        Duration untilExpiry = Duration.between(LocalDateTime.now(), expiresAt);
        if (untilExpiry.isNegative() || untilExpiry.isZero()) {
            return Duration.ZERO;
        }

        Duration cap = Duration.ofSeconds(appProperties.getRedirectCache().getTtlSeconds());
        return untilExpiry.compareTo(cap) < 0 ? untilExpiry : cap;
    }

    private String key(String shortCode) {
        return CACHE_KEY_PREFIX + shortCode;
    }
}
