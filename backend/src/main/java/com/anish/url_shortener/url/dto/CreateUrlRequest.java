package com.anish.url_shortener.url.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

import org.hibernate.validator.constraints.URL;

public record CreateUrlRequest(

        @NotBlank
        @URL
        String originalUrl,

        String customAlias,

        String title,

        @Size(max = 1000)
        String description,

        @Size(max = 1000)
        String tags,

        LocalDateTime expiresAt,

        @Size(min = 6, max = 128)
        String password,

        @Positive
        Long maxClicks

) {}
