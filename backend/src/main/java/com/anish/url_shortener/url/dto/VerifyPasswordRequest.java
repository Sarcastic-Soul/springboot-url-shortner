package com.anish.url_shortener.url.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPasswordRequest(
        @NotBlank String password
) {
}
