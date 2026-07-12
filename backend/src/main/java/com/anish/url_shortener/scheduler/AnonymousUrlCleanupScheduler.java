package com.anish.url_shortener.scheduler;

import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;
import com.anish.url_shortener.url.service.RedirectCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnonymousUrlCleanupScheduler {

    private final UrlRepository urlRepository;
    private final RedirectCacheService redirectCacheService;

    @Transactional
    @Scheduled(cron = "${app.cleanup.anonymous-expired-cron:0 */30 * * * *}")
    public void cleanupExpiredAnonymousUrls() {
        LocalDateTime now = LocalDateTime.now();
        List<Url> expiredAnonymousUrls = urlRepository.findAllByUserIsNullAndExpiresAtBefore(now);
        if (expiredAnonymousUrls.isEmpty()) {
            return;
        }

        for (Url url : expiredAnonymousUrls) {
            redirectCacheService.evict(url.getShortCode());
        }

        urlRepository.deleteAllInBatch(expiredAnonymousUrls);
        log.info("Deleted {} expired anonymous URLs", expiredAnonymousUrls.size());
    }
}
