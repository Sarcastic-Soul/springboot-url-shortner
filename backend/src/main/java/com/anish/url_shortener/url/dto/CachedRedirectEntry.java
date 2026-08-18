package com.anish.url_shortener.url.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param urlId the row this code points at. Carried in the cache so a cache-hit redirect can
 *              still record a click without a database read — without it, every cached
 *              redirect was invisible to analytics.
 */
public record CachedRedirectEntry(
        String originalUrl,
        LocalDateTime expiresAt,
        UUID urlId
) {
}
