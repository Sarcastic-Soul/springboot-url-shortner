package com.anish.url_shortener.url.service;

import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.analytics.repository.UrlClickRepository;
import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.url.dto.CreateUrlRequest;
import com.anish.url_shortener.url.dto.UpdateUrlRequest;
import com.anish.url_shortener.url.dto.UrlResponse;
import com.anish.url_shortener.url.entity.Url;
import com.anish.url_shortener.url.repository.UrlRepository;
import com.anish.url_shortener.util.ShortCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.anish.url_shortener.url.dto.UrlSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public UrlResponse create(CreateUrlRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        String code = request.customAlias();

        if (code == null || code.isBlank()) {

            do {
                code = ShortCodeGenerator.generate(7);
            } while (urlRepository.existsByShortCode(code));

        } else if (urlRepository.existsByShortCode(code)) {
            throw new IllegalArgumentException("Alias already exists");
        }

        Url url = Url.builder()
                .shortCode(code)
                .originalUrl(request.originalUrl())
                .title(request.title())
                .description(request.description())
                .user(user)
                .expiresAt(request.expiresAt())
                .build();

        urlRepository.save(url);

        return new UrlResponse(
                code,
                "http://localhost:8080/" + code,
                url.getOriginalUrl()
        );
    }

    public String getOriginalUrl(
            String shortCode,
            String ipAddress,
            String userAgent,
            String referer
    ) {

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new NoSuchElementException("Short URL not found"));

        if (!url.getIsActive()) {
            throw new IllegalArgumentException("Short URL is disabled");
        }

        url.setClickCount(url.getClickCount() + 1);

        urlClickRepository.save(

                UrlClick.builder()
                        .url(url)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .referer(referer)
                        .build()

        );

        urlRepository.save(url);

        return url.getOriginalUrl();
    }

    public Page<UrlSummaryResponse> getMyUrls(
            int page,
            int size,
            String search
    ){

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC,"createdAt")
        );

        Page<Url> urls;

        if(search == null || search.isBlank()){

            urls = urlRepository.findByUser(user,pageable);

        }else{

            urls = urlRepository
                    .findByUserAndTitleContainingIgnoreCaseOrUserAndOriginalUrlContainingIgnoreCase(
                            user,
                            search,
                            user,
                            search,
                            pageable
                    );
        }

        return urls.map(url -> new UrlSummaryResponse(

                url.getId(),

                url.getShortCode(),

                "http://localhost:8080/" + url.getShortCode(),

                url.getOriginalUrl(),

                url.getTitle(),

                url.getClickCount(),

                url.getIsActive(),

                url.getCreatedAt(),

                url.getExpiresAt()

        ));

    }

    public UrlSummaryResponse update(
            UUID id,
            UpdateUrlRequest request
    ) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Url url = urlRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("URL not found"));

        if (request.originalUrl() != null) {
            url.setOriginalUrl(request.originalUrl());
        }

        if (request.title() != null) {
            url.setTitle(request.title());
        }

        if (request.active() != null) {
            url.setIsActive(request.active());
        }

        if(request.expiresAt()!=null){
            url.setExpiresAt(request.expiresAt());
        }

        if (request.customAlias() != null &&
                !request.customAlias().equals(url.getShortCode())) {

            if (urlRepository.existsByShortCode(request.customAlias())) {
                throw new IllegalArgumentException("Alias already exists");
            }

            url.setShortCode(request.customAlias());
        }

        urlRepository.save(url);

        return new UrlSummaryResponse(
                url.getId(),
                url.getShortCode(),
                "http://localhost:8080/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getTitle(),
                url.getClickCount(),
                url.getIsActive(),
                url.getCreatedAt(),
                url.getExpiresAt()
        );
    }

    public void delete(UUID id) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        Url url = urlRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("URL not found"));

        urlRepository.delete(url);
    }

}
