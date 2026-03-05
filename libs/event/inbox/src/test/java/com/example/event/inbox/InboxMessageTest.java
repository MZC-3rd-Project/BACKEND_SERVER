package com.example.event.inbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InboxMessageTest {

    @Test
    void lifecycle_transitionsFromPendingToSucceeded() {
        InboxMessage message = InboxMessage.createPending("consumer", "evt-1", "UserCreated", "{}");

        LocalDateTime now = LocalDateTime.now();
        message.markProcessing(now, 30);
        message.markSucceeded(now.plusSeconds(1));

        assertThat(message.getStatus()).isEqualTo(InboxStatus.SUCCEEDED);
        assertThat(message.getProcessedAt()).isNotNull();
        assertThat(message.getLeaseUntil()).isNull();
    }

    @Test
    void markRetry_incrementsRetryAndSchedulesNextRetry() {
        InboxMessage message = InboxMessage.createPending("consumer", "evt-2", "UserEmailChanged", "{}");

        LocalDateTime now = LocalDateTime.now();
        message.markProcessing(now, 10);
        message.markRetry(now, now.plusSeconds(5), "temporary failure", 240);

        assertThat(message.getStatus()).isEqualTo(InboxStatus.PENDING);
        assertThat(message.getRetryCount()).isEqualTo(1);
        assertThat(message.getNextRetryAt()).isEqualTo(now.plusSeconds(5));
        assertThat(message.getLastError()).isEqualTo("temporary failure");
    }
}
