package com.anish.url_shortener.url.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.service.AsyncAnalyticsService;
import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.common.util.ShortCodeGenerator;
import com.anish.url_shortener.config.AppProperties;
import com.anish.url_shortener.url.dto.CachedRedirectEntry;
import com.anish.url_shortener.url.dto.CreateUrlRequest;
import com.anish.url_shortener.url.dto.RedirectDecision;
import com.anish.url_shortener.url.dto.UpdateUrlRequest;
import com.anish.url_shortener.url.dto.UrlResponse;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;

import com.anish.url_shortener.exception.ServiceOverloadedException;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.anish.url_shortener.url.dto.UrlSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UrlSafetyService urlSafetyService;
    private final AsyncAnalyticsService asyncAnalyticsService;
    private final RedirectCacheService redirectCacheService;
    private final UrlLookupService urlLookupService;
    private final AppProperties appProperties;

    // Creation is the one write on the hot path and the only operation that cannot scale
    // horizontally. Past the bulkhead's capacity callers get an immediate 503 with Retry-After
    // rather than a connection-pool timeout dressed up as a 500.
    @Bulkhead(name = "database", fallbackMethod = "shedCreate")
    public UrlResponse create(CreateUrlRequest request) {
        User user = getCurrentUserOrNull();
        boolean anonymous = user == null;

        String customAlias = request.customAlias();
        urlSafetyService.validateSafeDestination(request.originalUrl());
        validateCreationPayload(request, anonymous);

        String passwordHash = null;
        if (!anonymous && request.password() != null && !request.password().isBlank()) {
            passwordHash = passwordEncoder.encode(request.password());
        }

        boolean isCustomAlias = customAlias != null && !customAlias.isBlank();
        int maxRetries = isCustomAlias ? 1 : 5;
        Url savedUrl = null;

        for (int i = 0; i < maxRetries; i++) {
            String currentCode = isCustomAlias ? customAlias : shortCodeGenerator.generate();
            
            Url url = Url.builder()
                    .shortCode(currentCode)
                    .originalUrl(request.originalUrl())
                    .title(request.title())
                    .description(request.description())
                    .tags(request.tags())
                    .user(user)
                    .expiresAt(request.expiresAt())
                    .passwordHash(passwordHash)
                    .maxClicks(!anonymous ? request.maxClicks() : null)
                    .build();

            try {
                savedUrl = urlRepository.saveAndFlush(url);
                break; // successfully saved
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (isCustomAlias) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Alias already exists");
                }
                if (i == maxRetries - 1) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate unique short code");
                }
            }
        }
        
        if (savedUrl == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save URL");
        }

        redirectCacheService.putForAnonymousRedirect(savedUrl);

        return new UrlResponse(
                savedUrl.getShortCode(),
                appProperties.getBaseUrl() + "/" + savedUrl.getShortCode(),
                savedUrl.getOriginalUrl()
        );
    }

    @SuppressWarnings("unused")
    private UrlResponse shedCreate(CreateUrlRequest request, BulkheadFullException e) {
        throw new ServiceOverloadedException(1);
    }

    public RedirectDecision resolveRedirect(String shortCode, ClickContext clickContext) {
        Optional<CachedRedirectEntry> cachedRedirect = redirectCacheService.get(shortCode);
        if (cachedRedirect.isPresent()) {
            CachedRedirectEntry entry = cachedRedirect.get();
            if (entry.expiresAt() != null && entry.expiresAt().isBefore(LocalDateTime.now())) {
                redirectCacheService.evict(shortCode);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL has expired");
            }

            // The cache-hit path used to return here having recorded nothing, so the links that
            // served the most traffic were the ones with no analytics at all. Entries written
            // before urlId existed have none; those are skipped rather than dropped.
            if (entry.urlId() != null) {
                asyncAnalyticsService.trackClick(entry.urlId(), clickContext);
            }

            return new RedirectDecision(false, entry.originalUrl(), true);
        }

        Url url = urlLookupService.findRedirectableUrl(shortCode);

        if (url.getPasswordHash() != null && !url.getPasswordHash().isBlank()) {
            return new RedirectDecision(true, null, false);
        }

        // Read-through population. Without this the cache is only ever written on create and
        // update, so a link that was not created by recent activity -- or any link at all after
        // a Valkey eviction or restart -- misses on EVERY request and goes to Postgres forever.
        // The cache-first redirect path only holds if a miss teaches it something.
        redirectCacheService.putForAnonymousRedirect(url);

        return completeRedirect(url, clickContext);
    }

    public String verifyProtectedLink(String shortCode, String password, ClickContext clickContext) {
        Url url = urlLookupService.findRedirectableUrl(shortCode);

        if (url.getPasswordHash() == null || url.getPasswordHash().isBlank()) {
            return completeRedirect(url, clickContext).originalUrl();
        }

        if (!passwordEncoder.matches(password, url.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid link password");
        }

        return completeRedirect(url, clickContext).originalUrl();
    }

    public Page<UrlSummaryResponse> getMyUrls(
            int page,
            int size,
            String search
    ){

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC,"createdAt")
        );

        Page<Url> urls;

        if(search == null || search.isBlank()){

            urls = urlRepository.findByUser(user,pageable);

        }else{

            urls = urlRepository
                    .findByUserAndTitleContainingIgnoreCaseOrUserAndOriginalUrlContainingIgnoreCase(
                            user,
                            search,
                            user,
                            search,
                            pageable
                    );
        }

        return urls.map(url -> new UrlSummaryResponse(

                url.getId(),

                url.getShortCode(),

                appProperties.getBaseUrl() + "/" + url.getShortCode(),

                url.getOriginalUrl(),

                url.getTitle(),

                url.getClickCount(),

                url.getIsActive(),

                url.getCreatedAt(),

                url.getExpiresAt()

        ));

    }

    public UrlSummaryResponse update(
            UUID id,
            UpdateUrlRequest request
    ) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Url url = urlRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("URL not found"));

        if (request.originalUrl() != null) {
            url.setOriginalUrl(request.originalUrl());
        }

        if (request.title() != null) {
            url.setTitle(request.title());
        }

        if (request.description() != null) {
            url.setDescription(request.description());
        }

        if (request.tags() != null) {
            url.setTags(request.tags());
        }

        if (request.active() != null) {
            url.setIsActive(request.active());
        }

        if(request.expiresAt()!=null){
            url.setExpiresAt(request.expiresAt());
        }

        if (request.customAlias() != null &&
                !request.customAlias().equals(url.getShortCode())) {

            if (urlRepository.existsByShortCode(request.customAlias())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Alias already exists");
            }

            url.setShortCode(request.customAlias());
        }

        if (request.password() != null) {
            url.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.maxClicks() != null) {
            url.setMaxClicks(request.maxClicks());
        }

        urlRepository.save(url);
        redirectCacheService.putForAnonymousRedirect(url);

        return new UrlSummaryResponse(
                url.getId(),
                url.getShortCode(),
                appProperties.getBaseUrl() + "/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getTitle(),
                url.getClickCount(),
                url.getIsActive(),
                url.getCreatedAt(),
                url.getExpiresAt()
        );
    }

    public void delete(UUID id) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Url url = urlRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("URL not found"));

        url.setIsActive(false);
        urlRepository.save(url);
        redirectCacheService.evict(url.getShortCode());
    }

    public void deleteBulk(java.util.List<UUID> ids) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        java.util.List<Url> urls = urlRepository.findAllById(ids).stream()
                .filter(u -> u.getUser() != null && u.getUser().getId().equals(user.getId()))
                .toList();

        urls.forEach(url -> {
            url.setIsActive(false);
            redirectCacheService.evict(url.getShortCode());
        });
        
        urlRepository.saveAll(urls);
    }

    private RedirectDecision completeRedirect(Url url, ClickContext clickContext) {
        // No `urlRepository.save` here any more. That was the last blocking Postgres write on
        // the redirect path: one UPDATE per redirect, holding a pooled connection, for a counter
        // nobody reads synchronously. The consumer now folds these into one batched increment.
        //
        // The `url.getUser() != null` gate is gone too — it silently discarded every click on an
        // anonymous link, which is most of them.
        asyncAnalyticsService.trackClick(url.getId(), clickContext);

        return new RedirectDecision(false, url.getOriginalUrl(), false);
    }

    private void validateCreationPayload(CreateUrlRequest request, boolean anonymous) {
        if (!anonymous) {
            return;
        }

        if (request.expiresAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date is required for anonymous links");
        }

        LocalDateTime maxExpiry = LocalDateTime.now().plusDays(appProperties.getAnonymous().getMaxExpiryDays());
        if (request.expiresAt().isAfter(maxExpiry)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Anonymous expiry exceeds allowed window");
        }

        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Custom alias requires authentication");
        }

        if (request.description() != null || request.tags() != null || request.password() != null || request.maxClicks() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Advanced controls require authentication");
        }
    }

    private User getCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        return null;
    }

}
