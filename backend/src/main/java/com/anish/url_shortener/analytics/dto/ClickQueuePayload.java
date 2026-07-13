package com.anish.url_shortener.analytics.dto;

import java.util.UUID;

public record ClickQueuePayload(
    UUID urlId,
    String ipAddress,
    String ipHash,
    String country,
    String device,
    String browser,
    String os,
    String userAgent,
    String referer
) {}
