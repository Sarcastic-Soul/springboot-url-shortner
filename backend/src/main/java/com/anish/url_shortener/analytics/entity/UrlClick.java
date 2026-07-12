package com.anish.url_shortener.analytics.entity;

import com.anish.url_shortener.url.entity.Url;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "url_clicks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlClick {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime clickedAt = LocalDateTime.now();

    private String ipAddress;

    private String ipHash;

    private String country;

    private String device;

    private String browser;

    private String os;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String referer;

}
