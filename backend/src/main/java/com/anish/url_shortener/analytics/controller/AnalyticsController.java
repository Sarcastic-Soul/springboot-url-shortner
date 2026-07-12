package com.anish.url_shortener.analytics.controller;

import com.anish.url_shortener.analytics.dto.AnalyticsResponse;
import com.anish.url_shortener.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/{urlId}")
    public AnalyticsResponse analytics(
            @PathVariable UUID urlId
    ) {
        return analyticsService.getAnalytics(urlId);
    }

}
