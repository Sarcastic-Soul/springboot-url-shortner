package com.anish.url_shortener.security.ratelimit;

import com.anish.url_shortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;

    public boolean allowAnonymousCreate(String ipAddress) {
        AppProperties.Tier tier = appProperties.getRateLimit().getAnonymous();
        return allow("rl:create:ip:" + ipAddress, tier);
    }

    public boolean allowAuthenticatedCreate(String userId) {
        AppProperties.Tier tier = appProperties.getRateLimit().getAuthenticated();
        return allow("rl:create:user:" + userId, tier);
    }

    private boolean allow(String keyPrefix, AppProperties.Tier tier) {
        long windowSeconds = Math.max(1, tier.getRefillDurationMinutes() * 60);
        long window = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = keyPrefix + ":" + window;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds + 1));
        }

        return count != null && count <= tier.getCapacity();
    }
}
