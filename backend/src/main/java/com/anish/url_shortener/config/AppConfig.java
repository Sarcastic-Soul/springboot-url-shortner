package com.anish.url_shortener.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua_parser.Parser;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    Parser userAgentParser() {
        return new Parser();
    }

    // Not in SecurityConfig: that is servlet-only, and AuthService needs an encoder in task
    // pods too.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Click enqueueing, off the redirect's thread.
     *
     * <p>The rejection policy is the load-bearing part. {@code @Async} submits from the
     * <em>caller's</em> thread, so the default {@code AbortPolicy} threw
     * {@link java.util.concurrent.RejectedExecutionException} straight back into
     * {@code UrlService.resolveRedirect} — past the try/catch inside {@code trackClick}, which
     * only ever guarded the Redis write and runs on an analytics thread. A saturated queue
     * therefore turned cache-hit redirects into 500s: 14% of them under the CI gate, with no
     * {@code X-Cache} header on the way out, which is why the hit ratio still read 100%.
     *
     * <p>Dropping the click is the trade this path already claims to make — a click is worth
     * less than a redirect. The counter is what makes the drop visible instead of silent; a log
     * line per drop would be a log storm at exactly the moment the service is busiest.
     */
    @Bean(name = "analyticsExecutor")
    TaskExecutor analyticsExecutor(MeterRegistry meterRegistry) {
        Counter dropped = Counter.builder("analytics.clicks.dropped")
                .description("Clicks discarded because the analytics executor was saturated")
                .register(meterRegistry);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("analytics-");
        executor.setRejectedExecutionHandler((task, rejectingExecutor) -> dropped.increment());
        executor.initialize();
        return executor;
    }
}
