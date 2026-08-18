package com.anish.url_shortener.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process maintenance, for running the application without an orchestrator.
 *
 * <p><b>Off by default.</b> An {@code @Scheduled} method on a horizontally scaled deployment runs
 * once per replica: at fifteen replicas that was fifteen concurrent scans and batch deletes over
 * the same rows every thirty minutes, all contending for the connection pool they were competing
 * with real traffic for. In a cluster the chart runs these as CronJobs instead — one execution,
 * on its own pod, with its own connection.
 *
 * <p>The dev profile turns it on, so a laptop still cleans up after itself.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.maintenance.in-process-scheduler", havingValue = "true")
public class MaintenanceScheduler {

    private final AnonymousUrlCleanupService anonymousUrlCleanupService;
    private final ClickRetentionService clickRetentionService;

    @Scheduled(cron = "${app.maintenance.cleanup-cron:0 */30 * * * *}")
    public void cleanupExpiredAnonymousUrls() {
        anonymousUrlCleanupService.purgeExpired();
    }

    @Scheduled(cron = "${app.maintenance.retention-cron:0 15 3 * * *}")
    public void purgeOldClicks() {
        clickRetentionService.purgeOldClicks();
    }
}
