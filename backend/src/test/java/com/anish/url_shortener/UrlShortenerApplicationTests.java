package com.anish.url_shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole application, so it needs a real Postgres and Valkey:
 *
 * <pre>
 *   make dev-up
 *   RUN_INTEGRATION_TESTS=true ./mvnw test
 * </pre>
 *
 * Gated rather than deleted, and gated rather than left to fail: a suite that is red by default
 * is a suite nobody reads.
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class UrlShortenerApplicationTests {

	@Test
	void contextLoads() {
	}

}
