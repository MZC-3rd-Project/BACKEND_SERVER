package com.example.chat.scheduler;

import com.example.chat.config.ChatRetentionProperties;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.service.audit.ChatAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRetentionSchedulerTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatAuditService chatAuditService;

    private ChatRetentionScheduler chatRetentionScheduler;

    @BeforeEach
    void setUp() {
        ChatRetentionProperties properties = new ChatRetentionProperties();
        properties.setDays(365);
        chatRetentionScheduler = new ChatRetentionScheduler(properties, chatMessageRepository, chatAuditService);
    }

    @Test
    void purgeExpiredMessages_recordsAuditWhenDeleted() {
        when(chatMessageRepository.hardDeleteExpiredMessagesWithoutLegalHold(any(LocalDateTime.class))).thenReturn(3);

        chatRetentionScheduler.purgeExpiredMessages();

        verify(chatMessageRepository).hardDeleteExpiredMessagesWithoutLegalHold(any(LocalDateTime.class));
        verify(chatAuditService).logEvent(eq(null), eq(null), eq(null), eq(com.example.chat.entity.audit.ChatAuditEventType.RETENTION_DELETED), any());
    }

    @Test
    void purgeExpiredMessages_skipsAuditWhenNothingDeleted() {
        when(chatMessageRepository.hardDeleteExpiredMessagesWithoutLegalHold(any(LocalDateTime.class))).thenReturn(0);

        chatRetentionScheduler.purgeExpiredMessages();

        verify(chatAuditService, never()).logEvent(any(), any(), any(), any(), any());
    }
}
