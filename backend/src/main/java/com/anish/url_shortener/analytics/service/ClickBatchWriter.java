package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.url.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writes a batch of clicks in a fixed number of statements, whatever the batch size.
 *
 * <p>The consumer previously issued {@code findById} per message — 1,000 selects for a batch of
 * 1,000 — then relied on a synchronous {@code save} on the redirect path for the counter. Both
 * are gone: one existence query, one batched insert, one batched increment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickBatchWriter {

    private static final String INSERT_CLICK = """
            INSERT INTO url_clicks
                (id, url_id, clicked_at, ip_address, ip_hash, device, browser, os, user_agent, referer)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INCREMENT_COUNT =
            "UPDATE urls SET click_count = click_count + ? WHERE id = ?";

    private final UrlRepository urlRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * @return how many clicks were persisted; the rest referenced urls that no longer exist
     */
    @Transactional
    public int write(List<ClickRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        Set<UUID> referenced = new HashSet<>();
        rows.forEach(row -> referenced.add(row.urlId()));

        // One query instead of one per message, and a projection rather than whole entities:
        // the batch only needs to know which ids still exist, so a delete does not abort it on
        // a foreign key violation.
        Set<UUID> existing = new HashSet<>(urlRepository.findExistingIds(referenced));

        List<Object[]> inserts = new ArrayList<>(rows.size());
        Map<UUID, Long> increments = new HashMap<>();

        for (ClickRow row : rows) {
            if (!existing.contains(row.urlId())) {
                continue;
            }
            inserts.add(new Object[]{
                    UUID.randomUUID(),
                    row.urlId(),
                    Timestamp.valueOf(row.clickedAt()),
                    row.ipAddress(),
                    row.ipHash(),
                    row.device(),
                    row.browser(),
                    row.os(),
                    row.userAgent(),
                    row.referer()
            });
            increments.merge(row.urlId(), 1L, Long::sum);
        }

        if (inserts.isEmpty()) {
            return 0;
        }

        jdbcTemplate.batchUpdate(INSERT_CLICK, inserts);

        List<Object[]> counters = new ArrayList<>(increments.size());
        increments.forEach((urlId, delta) -> counters.add(new Object[]{delta, urlId}));
        jdbcTemplate.batchUpdate(INCREMENT_COUNT, counters);

        return inserts.size();
    }

    /** One click, already parsed off the stream. */
    public record ClickRow(
            UUID urlId,
            LocalDateTime clickedAt,
            String ipAddress,
            String ipHash,
            String device,
            String browser,
            String os,
            String userAgent,
            String referer
    ) {
    }
}
