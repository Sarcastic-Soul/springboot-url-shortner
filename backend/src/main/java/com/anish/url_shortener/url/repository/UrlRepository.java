package com.anish.url_shortener.url.repository;

import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.url.entity.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
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

    /**
     * Which of these ids still exist. A projection, not entities: the click consumer only needs
     * to avoid inserting against a deleted row, and loading whole urls to learn that was the
     * expensive half of its N+1.
     */
    @Query("select u.id from Url u where u.id in :ids")
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);

    /**
     * Deletes expired anonymous links in a single statement, backed by the partial index in
     * V7. Replaces load-then-deleteAllInBatch, which pulled every row into memory first.
     */
    @Modifying
    @Query("delete from Url u where u.user is null and u.expiresAt < :cutoff")
    int deleteExpiredAnonymous(@Param("cutoff") LocalDateTime cutoff);

    @Query("select u.shortCode from Url u where u.user is null and u.expiresAt < :cutoff")
    List<String> findExpiredAnonymousShortCodes(@Param("cutoff") LocalDateTime cutoff);
}
