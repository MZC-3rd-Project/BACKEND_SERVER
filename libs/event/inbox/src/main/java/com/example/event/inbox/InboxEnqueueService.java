package com.example.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxEnqueueService {

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
            log.debug("[InboxEnqueue] duplicate ignored. consumerName={}, eventId={}, eventType={}",
                    consumerName, eventId, eventType);
            return false;
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
}
