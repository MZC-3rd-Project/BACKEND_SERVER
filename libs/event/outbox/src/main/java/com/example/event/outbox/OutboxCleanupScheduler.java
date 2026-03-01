package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 발행 완료된 Outbox 메시지를 주기적으로 정리하여 테이블 무한 증가를 방지한다.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxProperties outboxProperties;

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 3 * * *}")
    public void cleanupPublishedMessages() {
        int retentionDays = Math.max(1, outboxProperties.getCleanup().getRetentionDays());
        int batchSize = Math.max(1, outboxProperties.getCleanup().getBatchSize());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        int deletedPublished = deleteInBatches(OutboxStatus.PUBLISHED, cutoff, batchSize);
        int deletedFailed = deleteInBatches(OutboxStatus.FAILED, cutoff, batchSize);

        if (deletedPublished > 0 || deletedFailed > 0) {
            log.info("[OutboxCleanup] Deleted {} published and {} failed messages older than {} days",
                    deletedPublished, deletedFailed, retentionDays);
        }
    }

    private int deleteInBatches(OutboxStatus status, LocalDateTime cutoff, int batchSize) {
        int totalDeleted = 0;
        while (true) {
            int deleted = outboxRepository.deleteTopByStatusAndCreatedBefore(status.name(), cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                return totalDeleted;
            }
        }
    }
}
