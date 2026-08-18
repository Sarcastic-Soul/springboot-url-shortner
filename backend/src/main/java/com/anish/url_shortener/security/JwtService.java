package com.anish.url_shortener.security;

import com.anish.url_shortener.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class JwtService {

    /** Carries the user id so authentication needs no database round trip. */
    public static final String CLAIM_USER_ID = "uid";

    @Value("${jwt.secret:6af69b2bbd4f4eecb14af72dfad91e0c71c73f8271dc3db889d9b2d0d8dffb86}")
    private String secret;

    @Value("${jwt.access-token-expiration:900000}")
    private long expiration;

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
