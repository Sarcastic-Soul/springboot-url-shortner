package com.anish.url_shortener.maintenance;

import com.anish.url_shortener.url.repository.UrlRepository;
import com.anish.url_shortener.url.service.RedirectCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnonymousUrlCleanupService {

    private final UrlRepository urlRepository;
    private final RedirectCacheService redirectCacheService;

    @Transactional
    public int purgeExpired() {
        LocalDateTime now = LocalDateTime.now();

        // Short codes first: the cache entries have to go, and after the delete there is nothing
        // left to read them from.
        List<String> shortCodes = urlRepository.findExpiredAnonymousShortCodes(now);
        if (shortCodes.isEmpty()) {
            return 0;
        }

        shortCodes.forEach(redirectCacheService::evict);

        int deleted = urlRepository.deleteExpiredAnonymous(now);
        log.info("Deleted {} expired anonymous URLs", deleted);
        return deleted;
    }
}
