package com.anish.url_shortener.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Email
        @NotBlank
        String email,

        @Size(min = 8, max = 100)
        String password

) {}
