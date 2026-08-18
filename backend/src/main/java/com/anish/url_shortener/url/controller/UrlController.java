package com.anish.url_shortener.url.controller;

import com.anish.url_shortener.url.dto.CreateUrlRequest;
import com.anish.url_shortener.url.dto.PasswordRequiredResponse;
import com.anish.url_shortener.url.dto.RedirectDecision;
import com.anish.url_shortener.url.dto.UpdateUrlRequest;
import com.anish.url_shortener.url.dto.UrlResponse;
import com.anish.url_shortener.url.dto.VerifyPasswordRequest;
import com.anish.url_shortener.url.dto.VerifyPasswordResponse;
import com.anish.url_shortener.url.service.UrlService;
import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.common.net.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.anish.url_shortener.url.dto.UrlSummaryResponse;
import org.springframework.data.domain.Page;

import java.net.URI;
import java.util.UUID;
import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/api/v1/urls")
    public UrlResponse create(
            @Valid @RequestBody CreateUrlRequest request
    ) {
        return urlService.create(request);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(

            @PathVariable String shortCode,

            HttpServletRequest request

    ) {
        ClickContext clickContext = clickContextFrom(request);
        RedirectDecision decision = urlService.resolveRedirect(shortCode, clickContext);

        if (decision.passwordRequired()) {
            return ResponseEntity.status(UNAUTHORIZED).body(
                    new PasswordRequiredResponse(
                            "PASSWORD_REQUIRED",
                            "This link requires a password."
                    )
            );
        }

        return ResponseEntity
                .status(302)
                .location(URI.create(decision.originalUrl()))
                // Lets a benchmark measure the cache rather than assume it. The same signal is
                // exported as a counter for Prometheus; this is the cheap per-request view.
                .header("X-Cache", decision.cacheHit() ? "HIT" : "MISS")
                .build();

    }

    @PostMapping("/api/v1/urls/{shortCode}/verify")
    public VerifyPasswordResponse verifyPassword(
            @PathVariable String shortCode,
            @Valid @RequestBody VerifyPasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        String originalUrl = urlService.verifyProtectedLink(
                shortCode,
                request.password(),
                clickContextFrom(servletRequest)
        );
        return new VerifyPasswordResponse(originalUrl);
    }

    @GetMapping("/api/v1/urls")
    public Page<UrlSummaryResponse> getMyUrls(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search

    ){

        return urlService.getMyUrls(
                page,
                size,
                search
        );

    }

    @PatchMapping("/api/v1/urls/{id}")
    public UrlSummaryResponse update(

            @PathVariable UUID id,

            @Valid
            @RequestBody UpdateUrlRequest request

    ) {

        return urlService.update(id, request);

    }

    @DeleteMapping("/api/v1/urls/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id
    ) {

        urlService.delete(id);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/urls/bulk")
    public ResponseEntity<Void> deleteBulk(
            @RequestBody List<UUID> ids
    ) {
        urlService.deleteBulk(ids);
        return ResponseEntity.noContent().build();
    }

    private ClickContext clickContextFrom(HttpServletRequest request) {
        return new ClickContext(
                // Was a second, independent copy of the X-Forwarded-For parsing that made the
                // rate limiter bypassable. There is one implementation of this now.
                clientIpResolver.resolve(request),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
        );
    }

}
