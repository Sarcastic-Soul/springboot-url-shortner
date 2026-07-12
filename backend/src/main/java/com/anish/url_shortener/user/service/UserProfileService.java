package com.anish.url_shortener.user.service;

import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.url.repository.UrlRepository;
import com.anish.url_shortener.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UrlRepository urlRepository;

    public UserProfileResponse getMyProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getEmailVerified(),
                user.getCreatedAt(),
                urlRepository.countByUser(user)
        );
    }
}
