package com.anish.url_shortener.security.ratelimit;

/**
 * @param allowed           whether the request may proceed
 * @param remainingTokens   tokens left in the bucket after this call
 * @param retryAfterSeconds how long until one token is available again
 */
public record RateLimitDecision(
        boolean allowed,
        long remainingTokens,
        long retryAfterSeconds
) {
    static RateLimitDecision allow(long remaining) {
        return new RateLimitDecision(true, remaining, 0);
    }
}
