package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.url.entity.Url;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsyncAnalyticsService {

    private final UrlClickRepository urlClickRepository;
    private final ClickEnrichmentService clickEnrichmentService;

    @Async("analyticsExecutor")
    public void trackClick(Url url, ClickContext context) {
        EnrichedClickContext enriched = clickEnrichmentService.enrich(context);

        UrlClick click = UrlClick.builder()
                .url(url)
                .ipAddress(context.ipAddress())
                .ipHash(enriched.ipHash())
                .country(enriched.country())
                .device(enriched.device())
                .browser(enriched.browser())
                .os(enriched.os())
                .userAgent(context.userAgent())
                .referer(context.referer())
                .build();

        urlClickRepository.save(click);
    }
}
