package com.anish.url_shortener.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The analytics executor's rejection policy is on the redirect's critical path, which is not
 * obvious from where it is declared.
 *
 * <p>{@code @Async} submits the task from the <em>caller's</em> thread — a Tomcat thread serving
 * a redirect. Under the default {@code AbortPolicy} a full queue therefore threw
 * {@link java.util.concurrent.RejectedExecutionException} back into
 * {@code UrlService.resolveRedirect}, past the try/catch inside {@code trackClick} (which guards
 * only the Redis write, and runs on an analytics thread). The CI gate saw it as 14% of redirects
 * returning 500 with no {@code X-Cache} header — so the measured cache-hit ratio stayed at a
 * flawless 100% while a seventh of the traffic failed.
 */
class AnalyticsExecutorTest {

    private static final String DROPPED = "analytics.clicks.dropped";

    @Test
    void aSaturatedQueueDropsTheClickInsteadOfFailingTheCaller() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = executor(registry);

        // Occupy every thread so nothing drains, then overfill the queue.
        CountDownLatch release = new CountDownLatch(1);
        for (int i = 0; i < poolCapacity(executor); i++) {
            executor.execute(() -> await(release));
        }

        try {
            // The caller is a request thread. It must survive backpressure, not inherit it.
            assertThatCode(() -> {
                for (int i = 0; i < 200; i++) {
                    executor.execute(() -> { });
                }
            }).doesNotThrowAnyException();

            assertThat(registry.counter(DROPPED).count())
                    .as("dropped clicks are counted, not silently discarded")
                    .isGreaterThan(0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void aClickThatFitsIsNotCountedAsDropped() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        ThreadPoolTaskExecutor executor = executor(registry);

        CountDownLatch ran = new CountDownLatch(1);
        executor.execute(ran::countDown);

        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.counter(DROPPED).count()).isZero();

        executor.shutdown();
    }

    private static ThreadPoolTaskExecutor executor(MeterRegistry registry) {
        TaskExecutor bean = new AppConfig().analyticsExecutor(registry);
        assertThat(bean).isInstanceOf(ThreadPoolTaskExecutor.class);
        return (ThreadPoolTaskExecutor) bean;
    }

    /** Everything the executor can hold at once: one task per thread, plus the queue. */
    private static int poolCapacity(ThreadPoolTaskExecutor executor) {
        return executor.getMaxPoolSize() + executor.getQueueCapacity();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
