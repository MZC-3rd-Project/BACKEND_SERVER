package com.example.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.sql.SQLException;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxEnqueueService {

    private static final String DUPLICATE_EVENT_CONSTRAINT = "uk_inbox_consumer_event";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final InboxRepository inboxRepository;
    private final InboxSignalPublisher inboxSignalPublisher;
    private final InboxProperties inboxProperties;

    @Transactional
    public boolean enqueue(String consumerName, String eventId, String eventType, String payload) {
        if (!StringUtils.hasText(consumerName)
                || !StringUtils.hasText(eventId)
                || !StringUtils.hasText(eventType)
                || !StringUtils.hasText(payload)) {
            return false;
        }

        try {
            inboxRepository.save(InboxMessage.createPending(consumerName, eventId, eventType, payload));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateEvent(e)) {
                log.debug("[InboxEnqueue] duplicate ignored. consumerName={}, eventId={}, eventType={}",
                        consumerName, eventId, eventType);
                return false;
            }
            throw e;
        }

        triggerAfterCommit(consumerName);
        return true;
    }

    private void triggerAfterCommit(String consumerName) {
        if (!inboxProperties.isImmediateTriggerEnabled()) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    inboxSignalPublisher.signal(consumerName);
                }
            });
            return;
        }

        inboxSignalPublisher.signal(consumerName);
    }

    private boolean isDuplicateEvent(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(DUPLICATE_EVENT_CONSTRAINT)) {
                return true;
            }

            if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
