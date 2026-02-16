package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void cleanupPublishedMessages() {
        int retentionDays = Math.max(1, outboxProperties.getCleanup().getRetentionDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        int deletedPublished = outboxRepository.deleteByStatusAndCreatedBefore(OutboxStatus.PUBLISHED, cutoff);
        int deletedFailed = outboxRepository.deleteByStatusAndCreatedBefore(OutboxStatus.FAILED, cutoff);

        if (deletedPublished > 0 || deletedFailed > 0) {
            log.info("[OutboxCleanup] Deleted {} published and {} failed messages older than {} days",
                    deletedPublished, deletedFailed, retentionDays);
        }
    }
}
