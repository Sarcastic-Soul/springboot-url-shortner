package com.anish.url_shortener.url.dto;

public record UrlResponse(

        String shortCode,

        String shortUrl,

        String originalUrl

) {}
