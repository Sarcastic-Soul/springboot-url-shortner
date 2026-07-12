package com.anish.url_shortener.url.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

import org.hibernate.validator.constraints.URL;

public record CreateUrlRequest(

        @NotBlank
        @URL
        String originalUrl,

        String customAlias,

        String title,

        String description,

        LocalDateTime expiresAt

) {}
