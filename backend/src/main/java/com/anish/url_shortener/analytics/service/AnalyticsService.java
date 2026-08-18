package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.AnalyticsResponse;
import com.anish.url_shortener.analytics.dto.ClickHistoryResponse;
import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public AnalyticsResponse getAnalytics(UUID urlId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Url url = urlRepository.findByIdAndUser(urlId, user)
                .orElseThrow(() -> new NoSuchElementException("URL not found"));

        // urls.click_count is the authoritative total. It and COUNT(*) over url_clicks used to
        // both be reported, in different screens, and they disagree: the counter is advanced for
        // every redirect, while rows are only kept for the retention window.
        long totalClicks = url.getClickCount() == null ? 0L : url.getClickCount();

        List<ClickHistoryResponse> clicks =
                urlClickRepository.findTop20ByUrlOrderByClickedAtDesc(url)
                        .stream()
                        .map(this::map)
                        .toList();

        return new AnalyticsResponse(
                totalClicks,
                clicks
        );
    }

    private ClickHistoryResponse map(UrlClick click) {

        return new ClickHistoryResponse(
                click.getClickedAt(),
                click.getIpAddress(),
                click.getDevice(),
                click.getBrowser(),
                click.getOs(),
                click.getReferer(),
                click.getUserAgent()
        );

    }

}
