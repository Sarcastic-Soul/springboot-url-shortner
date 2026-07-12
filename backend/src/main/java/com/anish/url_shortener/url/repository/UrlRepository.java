package com.anish.url_shortener.url.repository;

import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.url.entity.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface UrlRepository extends JpaRepository<Url, UUID> {

    boolean existsByShortCode(String shortCode);

    Optional<Url> findByShortCode(String shortCode);

    Page<Url> findByUser(User user, Pageable pageable);

    Page<Url> findByUserAndTitleContainingIgnoreCaseOrUserAndOriginalUrlContainingIgnoreCase(
            User user,
            String title,
            User user2,
            String originalUrl,
            Pageable pageable
    );

    Optional<Url> findByIdAndUser(UUID id, User user);

    long countByUser(User user);

    long deleteByUserIsNullAndExpiresAtBefore(LocalDateTime now);

    List<Url> findAllByUserIsNullAndExpiresAtBefore(LocalDateTime now);
}
