package com.anish.url_shortener.url.dto;

/**
 * @param cacheHit whether this was served from cache. Surfaced as {@code X-Cache} on the
 *                 response so a load test can report a hit ratio directly, instead of the
 *                 suite having to assume one.
 */
public record RedirectDecision(
        boolean passwordRequired,
        String originalUrl,
        boolean cacheHit
) {
}
