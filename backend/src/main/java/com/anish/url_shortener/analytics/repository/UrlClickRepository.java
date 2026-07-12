package com.anish.url_shortener.analytics.repository;

import com.anish.url_shortener.analytics.entity.UrlClick;
import com.anish.url_shortener.url.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UrlClickRepository extends JpaRepository<UrlClick, UUID> {

    long countByUrl(Url url);

    List<UrlClick> findTop20ByUrlOrderByClickedAtDesc(Url url);

}
