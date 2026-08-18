package com.anish.url_shortener.task;

import com.anish.url_shortener.maintenance.AnonymousUrlCleanupService;
import com.anish.url_shortener.maintenance.ClickRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs one named job and exits. Active only under the {@code task} profile.
 *
 * <p>This is how work that must happen <em>once</em> — schema migration, expiry cleanup, click
 * retention — stops happening once per replica. Kubernetes drives it as a Job or CronJob using
 * the same image and the same code as the running service, so there is no second copy of the
 * cleanup logic to drift out of sync.
 *
 * <pre>
 *   SPRING_PROFILES_ACTIVE=prod,task APP_TASK=cleanup-anonymous java -jar app.jar
 * </pre>
 */
@Slf4j
@Component
@Profile("task")
@RequiredArgsConstructor
public class TaskRunner implements ApplicationRunner {

    private final ApplicationContext applicationContext;
    private final AnonymousUrlCleanupService anonymousUrlCleanupService;
    private final ClickRetentionService clickRetentionService;

    @Value("${app.task.name:}")
    private String taskName;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;

        try {
            switch (taskName) {
                // Flyway has already run by the time any ApplicationRunner is called, so the
                // migrate task is the context starting successfully.
                case "migrate" -> log.info("Migrations applied");
                case "cleanup-anonymous" -> log.info("Cleanup removed {} expired anonymous urls",
                        anonymousUrlCleanupService.purgeExpired());
                case "clicks-retention" -> log.info("Retention removed {} click rows",
                        clickRetentionService.purgeOldClicks());
                default -> {
                    log.error("Unknown task '{}'. Expected one of: migrate, cleanup-anonymous, clicks-retention", taskName);
                    exitCode = 2;
                }
            }
        } catch (Exception e) {
            log.error("Task '{}' failed", taskName, e);
            exitCode = 1;
        }

        int finalExitCode = exitCode;
        System.exit(SpringApplication.exit(applicationContext, () -> finalExitCode));
    }
}
