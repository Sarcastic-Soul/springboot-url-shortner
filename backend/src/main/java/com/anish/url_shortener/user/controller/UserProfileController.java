package com.anish.url_shortener.user.controller;

import com.anish.url_shortener.user.dto.UserProfileResponse;
import com.anish.url_shortener.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponse me() {
        return userProfileService.getMyProfile();
    }
}
