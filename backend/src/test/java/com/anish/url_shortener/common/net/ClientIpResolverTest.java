package com.anish.url_shortener.common.net;

import com.anish.url_shortener.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    private AppProperties appProperties;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        resolver = new ClientIpResolver(appProperties);
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertEquals("10.0.0.7", resolver.resolve(request));
    }

    @Test
    void takesTheHopWeAppended_notTheOneTheClientChose() {
        appProperties.getClientIp().setTrustProxy(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        // A client prepending junk cannot displace the entry the proxy appends on the right.
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 198.51.100.4");

        assertEquals("198.51.100.4", resolver.resolve(request));
    }

    @Test
    void fallsBackToPeerAddressWhenTheTrustedHeaderIsAbsent() {
        appProperties.getClientIp().setTrustProxy(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");

        assertEquals("10.0.0.7", resolver.resolve(request));
    }
}
