package com.anish.url_shortener.maintenance;

import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickRetentionService {

    private final UrlClickRepository urlClickRepository;
    private final AppProperties appProperties;

    @Transactional
    public int purgeOldClicks() {
        int retentionDays = appProperties.getAnalytics().getRetentionDays();
        if (retentionDays <= 0) {
            log.info("Click retention disabled (app.analytics.retention-days={})", retentionDays);
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = urlClickRepository.deleteOlderThan(cutoff);
        log.info("Deleted {} url_clicks rows older than {}", deleted, cutoff);
        return deleted;
    }
}
