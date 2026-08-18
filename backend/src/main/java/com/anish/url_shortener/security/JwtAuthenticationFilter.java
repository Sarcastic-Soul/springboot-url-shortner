package com.anish.url_shortener.security;

import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwtService.parse(header.substring(7))
                .map(this::principalFrom)
                .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES)
                ));

        filterChain.doFilter(request, response);
    }

    /**
     * Builds the principal from the signed claims rather than loading it, removing one Postgres
     * query from every authenticated request. The token is signed by us, so its {@code uid} and
     * subject are as trustworthy as a row read — and considerably cheaper.
     *
     * <p>Tokens issued before the {@code uid} claim existed still work: they fall back to the
     * lookup until they expire.
     */
    private User principalFrom(Claims claims) {
        String userId = claims.get(JwtService.CLAIM_USER_ID, String.class);
        String email = claims.getSubject();

        if (userId == null) {
            return userRepository.findByEmail(email).orElse(null);
        }

        try {
            return User.builder()
                    .id(UUID.fromString(userId))
                    .email(email)
                    .build();
        } catch (IllegalArgumentException e) {
            return userRepository.findByEmail(email).orElse(null);
        }
    }
}
