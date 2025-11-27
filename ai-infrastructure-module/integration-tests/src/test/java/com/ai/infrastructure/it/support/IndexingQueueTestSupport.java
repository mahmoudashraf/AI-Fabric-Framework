package com.ai.infrastructure.it.support;

import com.ai.infrastructure.config.AIIndexingProperties;
import com.ai.infrastructure.entity.IndexingQueueEntry;
import com.ai.infrastructure.indexing.IndexingStrategy;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.indexing.worker.IndexingWorkProcessor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Test-only utility that allows integration tests to deterministically drain the indexing queue
 * instead of waiting for the scheduled workers to pick up work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingQueueTestSupport {

    private final IndexingQueueService queueService;
    private final IndexingWorkProcessor workProcessor;
    private final AIIndexingProperties indexingProperties;

    /**
     * Drain both async and batch indexing queues once, best-effort.
     */
    public void drainQueue() {
        drainStrategy(IndexingStrategy.ASYNC, Math.max(1, indexingProperties.getAsyncWorker().getBatchSize()));
        drainStrategy(IndexingStrategy.BATCH, Math.max(1, indexingProperties.getBatchWorker().getBatchSize()));
    }

    private void drainStrategy(IndexingStrategy strategy, int batchSize) {
        int iteration = 0;
        int maxIterations = 50;
        int idleCycles = 0;
        int maxIdleCycles = 5;
        while (iteration++ < maxIterations && idleCycles < maxIdleCycles) {
            List<IndexingQueueEntry> entries = queueService.lease(strategy, batchSize);
            if (entries.isEmpty()) {
                idleCycles++;
                sleepQuietly(50);
                continue;
            }
            idleCycles = 0;
            for (IndexingQueueEntry entry : entries) {
                try {
                    workProcessor.process(entry);
                    queueService.markCompleted(entry);
                } catch (Exception ex) {
                    log.error("Failed to process indexing entry {} via test drain", entry.getId(), ex);
                    queueService.markFailure(entry, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                }
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
