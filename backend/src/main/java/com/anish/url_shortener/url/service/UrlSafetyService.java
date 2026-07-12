package com.anish.url_shortener.url.service;

import com.anish.url_shortener.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlSafetyService {

    private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "file");

    private final AppProperties appProperties;

    public void validateSafeDestination(String originalUrl) {
        URI uri;
        try {
            uri = URI.create(originalUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid destination URL");
        }

        if (uri.getScheme() == null || BLOCKED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported URL scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid destination host");
        }

        Set<String> blockedDomains = appProperties.getUrlSafety().getBlockedDomains()
                .stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (blockedDomains.contains(normalizedHost)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination URL is blocked");
        }
    }
}
