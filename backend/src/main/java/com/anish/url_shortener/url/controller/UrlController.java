package com.anish.url_shortener.url.controller;

import com.anish.url_shortener.url.dto.CreateUrlRequest;
import com.anish.url_shortener.url.dto.UpdateUrlRequest;
import com.anish.url_shortener.url.dto.UrlResponse;
import com.anish.url_shortener.url.service.UrlService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.anish.url_shortener.url.dto.UrlSummaryResponse;
import org.springframework.data.domain.Page;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/api/v1/urls")
    public UrlResponse create(
            @Valid @RequestBody CreateUrlRequest request
    ) {
        return urlService.create(request);
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(

            @PathVariable String shortCode,

            HttpServletRequest request

    ) {

        String originalUrl = urlService.getOriginalUrl(

                shortCode,

                request.getRemoteAddr(),

                request.getHeader("User-Agent"),

                request.getHeader("Referer")

        );

        return ResponseEntity
                .status(302)
                .location(URI.create(originalUrl))
                .build();

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

}
