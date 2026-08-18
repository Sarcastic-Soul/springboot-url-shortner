package com.anish.url_shortener.config;

import com.anish.url_shortener.security.JwtAuthenticationFilter;
import com.anish.url_shortener.security.ratelimit.UrlCreationRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Servlet-only. Task pods run with no web application context, so {@code HttpSecurity} does not
 * exist there and this configuration cannot be created — excluding it is what lets the same
 * image serve traffic and run a CronJob.
 *
 * <p>{@code PasswordEncoder} deliberately lives in {@link AppConfig} instead: {@code AuthService}
 * needs it in every context, web or not.
 */
@Configuration
@Profile("!task")
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UrlCreationRateLimitFilter urlCreationRateLimitFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .httpBasic(httpBasic -> httpBasic.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/urls/*/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/*").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        urlCreationRateLimitFilter,
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }

}
