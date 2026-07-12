package com.anish.url_shortener.analytics.dto;

import java.time.LocalDateTime;

public record ClickHistoryResponse(

        LocalDateTime clickedAt,
        String ipAddress,
        String country,
        String device,
        String browser,
        String os,
        String referer,
        String userAgent

) {}
