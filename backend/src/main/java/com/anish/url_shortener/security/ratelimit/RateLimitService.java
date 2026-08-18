package com.anish.url_shortener.security.ratelimit;

import com.anish.url_shortener.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Distributed token bucket over Valkey/Redis.
 *
 * <p>The bucket state and the refill arithmetic both live inside a single Lua script, so a
 * check is one atomic round trip. See {@code scripts/token_bucket.lua} for why the previous
 * fixed-window INCR was not good enough.
 */
@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final RedisScript<List> tokenBucketScript;

    public RateLimitService(StringRedisTemplate redisTemplate, AppProperties appProperties) {
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        script.setResultType(List.class);
        this.tokenBucketScript = script;
    }

    public RateLimitDecision allowAnonymousCreate(String ipAddress) {
        return allow("rl:create:ip:" + ipAddress, appProperties.getRateLimit().getAnonymous());
    }

    public RateLimitDecision allowAuthenticatedCreate(String userId) {
        return allow("rl:create:user:" + userId, appProperties.getRateLimit().getAuthenticated());
    }

    private RateLimitDecision allow(String key, AppProperties.Tier tier) {
        long capacity = Math.max(1, tier.getCapacity());
        long refillTokens = Math.max(1, tier.getRefillTokens());
        long refillIntervalMs = Math.max(1, tier.getRefillDurationMinutes()) * 60_000L;

        List<?> result;
        try {
            result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    Long.toString(capacity),
                    Long.toString(refillTokens),
                    Long.toString(refillIntervalMs),
                    "1"
            );
        } catch (RuntimeException e) {
            // Fail open. A cache outage should degrade abuse protection, not availability --
            // the ingress-level limiter is still in front of us. Logged so it is visible.
            log.warn("Rate limit check failed for {}; allowing the request", key, e);
            return RateLimitDecision.allow(-1);
        }

        if (result == null || result.size() < 2) {
            log.warn("Unexpected token bucket reply for {}: {}", key, result);
            return RateLimitDecision.allow(-1);
        }

        boolean allowed = ((Number) result.get(0)).longValue() == 1L;
        long remaining = ((Number) result.get(1)).longValue();

        // Seconds until one more token exists. Bounded below by 1 so a client that honours
        // Retry-After never busy-loops.
        long retryAfter = Math.max(1, Math.round(Math.ceil((double) refillIntervalMs / refillTokens / 1000d)));

        return new RateLimitDecision(allowed, remaining, allowed ? 0 : retryAfter);
    }
}
