package com.anish.url_shortener.security;

import com.anish.url_shortener.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class JwtService {

    /** Carries the user id so authentication needs no database round trip. */
    public static final String CLAIM_USER_ID = "uid";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:900000}")
    private long expiration;

    /**
     * Fails the startup rather than the first login. HS512 needs at least 32 bytes, and
     * {@code Keys.hmacShaKeyFor} would otherwise throw on the first token issued — turning a
     * misconfiguration into a runtime error for whoever happens to sign up first.
     */
    @PostConstruct
    void validateSecret() {
        int bytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes for HS256; got " + bytes
                            + ". Set the JWT_SECRET environment variable."
            );
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {

        Date now = new Date();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim(CLAIM_USER_ID, user.getId().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key())
                .compact();
    }

    /**
     * Parses and verifies in one pass. The previous shape — {@code isValid()} followed by
     * {@code extractEmail()} — verified the signature twice on every authenticated request.
     *
     * @return the claims, or empty when the token is absent, malformed, expired or unsigned by us
     */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(
                    Jwts.parser()
                            .verifyWith(key())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload()
            );
        } catch (Exception e) {
            log.debug("Rejected JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
