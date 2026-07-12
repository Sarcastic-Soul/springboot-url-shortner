package com.anish.url_shortener.url.dto;

public record RedirectDecision(
        boolean passwordRequired,
        String originalUrl
) {
}
