package com.anish.url_shortener.common.net;

import com.anish.url_shortener.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single authority for "who is this request from".
 *
 * <p>The previous implementation — duplicated in {@code UrlController} and the rate-limit
 * filter — read {@code X-Forwarded-For} unconditionally and took the <em>leftmost</em> entry.
 * Both halves of that are wrong:
 *
 * <ul>
 *   <li>Unconditionally: when nothing sits in front of the application, the header is written
 *       entirely by the client. A fresh value per request meant a fresh rate-limit bucket per
 *       request, so the limiter enforced nothing at all.</li>
 *   <li>Leftmost: even behind a proxy, the left of the list is whatever the client sent. Only
 *       the <em>rightmost</em> entry was appended by the hop we control.</li>
 * </ul>
 *
 * <p>So the header is consulted only when {@code app.client-ip.trust-proxy} is on — which the
 * chart sets exactly when the ingress is in the path and overwriting the header — and even
 * then the rightmost entry wins.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AppProperties appProperties;

    public String resolve(HttpServletRequest request) {
        AppProperties.ClientIp config = appProperties.getClientIp();

        if (!config.isTrustProxy()) {
            return request.getRemoteAddr();
        }

        String header = request.getHeader(config.getHeader());
        if (header == null || header.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] hops = header.split(",");
        String closest = hops[hops.length - 1].trim();
        return closest.isEmpty() ? request.getRemoteAddr() : closest;
    }
}
