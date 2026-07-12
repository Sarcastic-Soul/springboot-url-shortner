package com.anish.url_shortener.analytics.dto;

public record ClickContext(
        String ipAddress,
        String userAgent,
        String referer
) {
}
