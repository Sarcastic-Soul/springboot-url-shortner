package com.anish.url_shortener.analytics.dto;

public record EnrichedClickContext(
        String ipHash,
        String country,
        String device,
        String browser,
        String os
) {
}
