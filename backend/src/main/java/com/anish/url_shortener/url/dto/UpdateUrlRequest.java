package com.anish.url_shortener.url.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUrlRequest(

        @Pattern(regexp = "https?://.+")
        String originalUrl,

        @Size(max = 255)
        String title,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{4,30}$")
        String customAlias,

        Boolean active,

        LocalDateTime expiresAt

) {}
