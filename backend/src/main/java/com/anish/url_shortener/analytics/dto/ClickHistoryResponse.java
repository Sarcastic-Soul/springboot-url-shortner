package com.anish.url_shortener.analytics.dto;

import java.time.LocalDateTime;

public record ClickHistoryResponse(

        LocalDateTime clickedAt,
        String ipAddress,
        String referer,
        String userAgent

) {}
