package com.example.event.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 발행 완료된 Outbox 메시지를 주기적으로 정리하여 테이블 무한 증가를 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private static final int RETENTION_DAYS = 7;

    private final OutboxRepository outboxRepository;

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    @Transactional
    public void cleanupPublishedMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);

        int deletedPublished = outboxRepository.deleteByStatusAndCreatedBefore(OutboxStatus.PUBLISHED, cutoff);
        int deletedFailed = outboxRepository.deleteByStatusAndCreatedBefore(OutboxStatus.FAILED, cutoff);

        if (deletedPublished > 0 || deletedFailed > 0) {
            log.info("[OutboxCleanup] Deleted {} published and {} failed messages older than {} days",
                    deletedPublished, deletedFailed, RETENTION_DAYS);
        }
    }
}
