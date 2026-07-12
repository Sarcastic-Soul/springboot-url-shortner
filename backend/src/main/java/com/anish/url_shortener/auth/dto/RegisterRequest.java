package com.anish.url_shortener.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 30)
        String username,

        @Email
        @NotBlank
        String email,

        @Size(min = 8, max = 100)
        String password

) {}
