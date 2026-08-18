package com.anish.url_shortener.url.service;

import com.anish.url_shortener.exception.ServiceOverloadedException;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * The database-bound half of a redirect, isolated behind a bulkhead.
 *
 * <p>Cache hits never come through here. Only a miss does, and a miss is the one part of the
 * redirect path that consumes a connection. Capping concurrency here means a cache-miss storm
 * sheds load as fast 503s instead of parking every Tomcat thread on the connection pool until
 * it times out — which is precisely how a 2.3s p95 and an 88% success rate were produced.
 */
@Service
@RequiredArgsConstructor
public class UrlLookupService {

    private final UrlRepository urlRepository;
    private final RedirectCacheService redirectCacheService;

    @Bulkhead(name = "database", fallbackMethod = "shed")
    public Url findRedirectableUrl(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));

        if (!url.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Short URL is inactive");
        }

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            deactivate(url);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL has expired");
        }

        // clickCount is now advanced by the analytics consumer in batches, so this ceiling is
        // enforced within a batch interval rather than instantly. Links with a maxClicks limit
        // are never cached, so every one of their redirects still reaches this check.
        if (url.getMaxClicks() != null && url.getClickCount() >= url.getMaxClicks()) {
            deactivate(url);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL is no longer available");
        }

        return url;
    }

    @SuppressWarnings("unused")
    private Url shed(String shortCode, BulkheadFullException e) {
        throw new ServiceOverloadedException(1);
    }

    private void deactivate(Url url) {
        url.setIsActive(false);
        urlRepository.save(url);
        redirectCacheService.evict(url.getShortCode());
    }
}
