package com.anish.url_shortener.analytics.service;

import com.anish.url_shortener.url.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickBatchWriterTest {

    @Mock private UrlRepository urlRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private ClickBatchWriter writer;

    private ClickBatchWriter.ClickRow row(UUID urlId) {
        return new ClickBatchWriter.ClickRow(
                urlId, LocalDateTime.now(), "10.0.0.1", "hash", "Mobile", "Chrome", "Android", "ua", null
        );
    }

    /** The N+1 this replaced issued one findById per message. A batch of 300 must not do that. */
    @Test
    void resolvesEveryUrlInOneQueryAndWritesInTwoBatches() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        List<ClickBatchWriter.ClickRow> rows = List.of(row(a), row(a), row(b));
        when(urlRepository.findExistingIds(anyCollection())).thenReturn(List.of(a, b));

        assertEquals(3, writer.write(rows));

        verify(urlRepository, times(1)).findExistingIds(anyCollection());
        verify(urlRepository, never()).findById(any());

        ArgumentCaptor<List<Object[]>> batches = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), batches.capture());

        assertEquals(3, batches.getAllValues().get(0).size(), "one insert per click");
        assertEquals(2, batches.getAllValues().get(1).size(), "one increment per distinct url");
    }

    /** Clicks for a url deleted between enqueue and drain must not abort the whole batch. */
    @Test
    void skipsClicksWhoseUrlHasBeenDeleted() {
        UUID alive = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();

        when(urlRepository.findExistingIds(anyCollection())).thenReturn(List.of(alive));

        assertEquals(1, writer.write(List.of(row(alive), row(deleted))));
    }

    @Test
    void writesNothingForAnEmptyBatch() {
        assertEquals(0, writer.write(List.of()));
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(urlRepository);
    }
}
