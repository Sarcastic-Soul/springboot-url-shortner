package com.anish.url_shortener.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private Anonymous anonymous = new Anonymous();
    private UrlSafety urlSafety = new UrlSafety();
    private GeoIp geoip = new GeoIp();
    private RateLimit rateLimit = new RateLimit();
    private RedirectCache redirectCache = new RedirectCache();

    @Getter
    @Setter
    public static class Anonymous {
        private int maxExpiryDays = 7;
    }

    @Getter
    @Setter
    public static class UrlSafety {
        private List<String> blockedDomains = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class GeoIp {
        private String databasePath;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private Tier anonymous = new Tier();
        private Tier authenticated = new Tier();
    }

    @Getter
    @Setter
    public static class Tier {
        private long capacity = 100000;
        private long refillTokens = 100000;
        private long refillDurationMinutes = 1;
    }

    @Getter
    @Setter
    public static class RedirectCache {
        private long ttlSeconds = 86400;
    }
}
