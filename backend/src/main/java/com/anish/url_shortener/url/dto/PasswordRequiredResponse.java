package com.anish.url_shortener.url.dto;

public record PasswordRequiredResponse(
        String code,
        String message
) {
}
