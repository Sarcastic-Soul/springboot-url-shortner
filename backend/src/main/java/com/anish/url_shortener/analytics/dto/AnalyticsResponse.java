package com.anish.url_shortener.analytics.dto;

import java.util.List;

public record AnalyticsResponse(

        long totalClicks,
        List<ClickHistoryResponse> recentClicks

) {}
