package com.anish.url_shortener.analytics.repository;

import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.url.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UrlClickRepository extends JpaRepository<UrlClick, UUID> {

    List<UrlClick> findTop20ByUrlOrderByClickedAtDesc(Url url);

    /**
     * The table has no natural ceiling — a single soak run adds hundreds of thousands of rows.
     * Driven by the {@code clicks-retention} task, not by a scheduler on every replica.
     */
    @Modifying
    @Query("delete from UrlClick c where c.clickedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
