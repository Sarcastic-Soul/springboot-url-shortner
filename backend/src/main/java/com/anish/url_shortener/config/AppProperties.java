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
    private RateLimit rateLimit = new RateLimit();
    private RedirectCache redirectCache = new RedirectCache();
    private ClientIp clientIp = new ClientIp();
    private Analytics analytics = new Analytics();

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
    public static class RateLimit {
        private boolean enabled = true;
        private Tier anonymous = new Tier();
        private Tier authenticated = new Tier();
        /**
         * Presented by a load generator in {@code X-RateLimit-Bypass} to skip the limiter.
         * Empty means no caller can ever bypass it, which is the production setting.
         */
        private String bypassSecret = "";
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

    /**
     * How to identify the caller. See
     * {@link com.anish.url_shortener.common.net.ClientIpResolver} for why this is not simply
     * "read X-Forwarded-For".
     */
    @Getter
    @Setter
    public static class ClientIp {
        /**
         * Only enable where a proxy that <em>overwrites</em> the header is guaranteed to be in
         * front of the application. If clients can reach it directly, this makes per-IP rate
         * limiting bypassable with one header.
         */
        private boolean trustProxy = false;
        private String header = "X-Forwarded-For";
    }

    @Getter
    @Setter
    public static class Analytics {
        /** Approximate MAXLEN applied on write, so the stream is bounded whatever the consumer does. */
        private long streamMaxLength = 100_000;
        private int batchSize = 1000;
        /** How long an unacknowledged message may sit before another pod reclaims it. */
        private long reclaimIdleMs = 60_000;
        /** Days of click history to keep; 0 or less disables the retention task. */
        private int retentionDays = 90;
    }
}
