package com.anish.url_shortener.url.service;

import com.anish.url_shortener.config.AppProperties;
import com.anish.url_shortener.url.dto.CachedRedirectEntry;
import com.anish.url_shortener.url.entity.Url;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class RedirectCacheService {

    private static final String CACHE_KEY_PREFIX = "redirect:code:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Cache<String, CachedRedirectEntry> localRedirectCache;

    // The redirect path is supposed to be cache-dominated. Without these, "supposed to be" was
    // the whole of the evidence: nothing in the system reported an actual hit ratio.
    private final Counter localHits;
    private final Counter remoteHits;
    private final Counter misses;

    public RedirectCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            Cache<String, CachedRedirectEntry> localRedirectCache,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.localRedirectCache = localRedirectCache;
        this.localHits = lookupCounter(meterRegistry, "local_hit");
        this.remoteHits = lookupCounter(meterRegistry, "remote_hit");
        this.misses = lookupCounter(meterRegistry, "miss");
    }

    private static Counter lookupCounter(MeterRegistry registry, String result) {
        return Counter.builder("redirect.cache.lookups")
                .description("Redirect cache lookups by where the answer came from")
                .tag("result", result)
                .register(registry);
    }

    @CircuitBreaker(name = "redisCache", fallbackMethod = "fallbackGet")
    public Optional<CachedRedirectEntry> get(String shortCode) {
        CachedRedirectEntry localEntry = localRedirectCache.getIfPresent(shortCode);
        if (localEntry != null) {
            localHits.increment();
            return Optional.of(localEntry);
        }

        String value = redisTemplate.opsForValue().get(key(shortCode));
        if (value == null) {
            misses.increment();
            return Optional.empty();
        }

        try {
            CachedRedirectEntry entry = objectMapper.readValue(value, CachedRedirectEntry.class);
            localRedirectCache.put(shortCode, entry);
            remoteHits.increment();
            return Optional.of(entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize redirect cache for {}", shortCode, e);
            redisTemplate.delete(key(shortCode));
            misses.increment();
            return Optional.empty();
        }
    }

    public Optional<CachedRedirectEntry> fallbackGet(String shortCode, Throwable t) {
        log.warn("Redis is unavailable, falling back to database for {}", shortCode);
        misses.increment();
        return Optional.empty();
    }

    @CircuitBreaker(name = "redisCache", fallbackMethod = "fallbackPut")
    public void putForAnonymousRedirect(Url url) {
        if (!isCacheableAnonymousRedirect(url)) {
            evict(url.getShortCode());
            return;
        }

        CachedRedirectEntry entry = new CachedRedirectEntry(url.getOriginalUrl(), url.getExpiresAt(), url.getId());

        try {
            String value = objectMapper.writeValueAsString(entry);
            Duration ttl = resolveTtl(url.getExpiresAt());
            if (ttl.isNegative() || ttl.isZero()) {
                evict(url.getShortCode());
                return;
            }
            redisTemplate.opsForValue().set(key(url.getShortCode()), value, ttl);
            localRedirectCache.put(url.getShortCode(), entry);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize redirect cache for {}", url.getShortCode(), e);
        }
    }

    public void fallbackPut(Url url, Throwable t) {
        log.warn("Redis is unavailable, skipping cache put for {}", url.getShortCode());
    }

    @CircuitBreaker(name = "redisCache", fallbackMethod = "fallbackEvict")
    public void evict(String shortCode) {
        redisTemplate.delete(key(shortCode));
        localRedirectCache.invalidate(shortCode);
    }

    public void fallbackEvict(String shortCode, Throwable t) {
        log.warn("Redis is unavailable, skipping cache evict for {}", shortCode);
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
