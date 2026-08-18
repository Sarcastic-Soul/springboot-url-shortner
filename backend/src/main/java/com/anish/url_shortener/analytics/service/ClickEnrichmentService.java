package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives what can be derived from the request itself: a hashed IP for unique-visitor counting,
 * and device/browser/OS from the user agent.
 *
 * <p>Geographic resolution used to live here. It never worked — {@code app.geoip.database-path}
 * was empty in every profile, so the reader was always null and every click was filed as
 * "Unknown" while the README advertised geographic analytics. Rather than ship a MaxMind
 * database and a licence-key rotation job for a feature nothing depended on, the column, the
 * dependency and the claim were all removed together.
 */
@Service
@RequiredArgsConstructor
public class ClickEnrichmentService {

    private final Parser parser;

    public EnrichedClickContext enrich(ClickContext context) {
        Client client = parseUserAgent(context.userAgent());

        return new EnrichedClickContext(
                hashIp(context.ipAddress()),
                client.device != null ? client.device.family : "Unknown",
                client.userAgent != null ? client.userAgent.family : "Unknown",
                client.os != null ? client.os.family : "Unknown"
        );
    }

    private Client parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new Client(null, null, null);
        }
        return parser.parse(userAgent);
    }

    private String hashIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ipAddress.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
