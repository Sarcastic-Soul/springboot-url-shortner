package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.analytics.dto.ClickContext;
import com.anish.url_shortener.analytics.dto.EnrichedClickContext;
import com.anish.url_shortener.config.AppProperties;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
public class ClickEnrichmentService {

    private final Parser parser;
    private final DatabaseReader databaseReader;

    public ClickEnrichmentService(Parser parser, AppProperties appProperties) {
        this.parser = parser;
        this.databaseReader = buildReader(appProperties.getGeoip().getDatabasePath());
    }

    public EnrichedClickContext enrich(ClickContext context) {
        String ipHash = hashIp(context.ipAddress());
        String country = resolveCountry(context.ipAddress());
        Client client = parseUserAgent(context.userAgent());

        return new EnrichedClickContext(
                ipHash,
                country,
                client.device != null ? client.device.family : "Unknown",
                client.userAgent != null ? client.userAgent.family : "Unknown",
                client.os != null ? client.os.family : "Unknown"
        );
    }

    private String resolveCountry(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "Unknown";
        }

        if (databaseReader == null) {
            return "Unknown";
        }
        try {
            return databaseReader.country(InetAddress.getByName(ipAddress)).getCountry().getIsoCode();
        } catch (IOException | GeoIp2Exception e) {
            log.debug("Unable to resolve country for IP", e);
            return "Unknown";
        }
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

    private DatabaseReader buildReader(String databasePath) {
        if (databasePath == null || databasePath.isBlank()) {
            return null;
        }

        Path path = Path.of(databasePath);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            return new DatabaseReader.Builder(new File(databasePath)).build();
        } catch (IOException e) {
            log.warn("Unable to initialize GeoIP database reader", e);
            return null;
        }
    }

    @PreDestroy
    void closeReader() throws IOException {
        if (databaseReader != null) {
            databaseReader.close();
        }
    }
}
