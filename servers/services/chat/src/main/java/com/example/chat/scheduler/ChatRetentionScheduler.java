package com.example.chat.scheduler;

import com.example.chat.config.ChatRetentionProperties;
import com.example.chat.entity.audit.ChatAuditEventType;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.service.audit.ChatAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRetentionScheduler {

    private final ChatRetentionProperties chatRetentionProperties;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAuditService chatAuditService;

    @Scheduled(cron = "${chat.retention.cleanup-cron:0 15 4 * * *}")
    @Transactional
    public void purgeExpiredMessages() {
        int retentionDays = Math.max(1, chatRetentionProperties.getDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deletedCount = chatMessageRepository.hardDeleteExpiredMessagesWithoutLegalHold(cutoff);

        if (deletedCount <= 0) {
            return;
        }

        log.info("Chat retention deleted {} messages older than {} days", deletedCount, retentionDays);
        chatAuditService.logEvent(
                null,
                null,
                null,
                ChatAuditEventType.RETENTION_DELETED,
                Map.of(
                        "deletedCount", deletedCount,
                        "cutoff", cutoff.toString(),
                        "retentionDays", retentionDays
                )
        );
    }
}
