package com.example.event.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<InboxSignalPublisher> inboxSignalPublisherProvider;
    private final InboxProperties inboxProperties;

    @Transactional
    public boolean enqueue(String consumerName, String eventId, String eventType, String payload) {
        if (!StringUtils.hasText(consumerName)
                || !StringUtils.hasText(eventId)
                || !StringUtils.hasText(eventType)
                || !StringUtils.hasText(payload)) {
            return false;
        }

        int inserted = inboxRepository.insertPendingIgnoreDuplicate(
                consumerName,
                eventId,
                eventType,
                payload,
                InboxStatus.PENDING.name()
        );
        if (inserted == 0) {
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
                    signalNow(consumerName);
                }
            });
            return;
        }

        signalNow(consumerName);
    }

    private void signalNow(String consumerName) {
        InboxSignalPublisher inboxSignalPublisher = inboxSignalPublisherProvider.getIfAvailable();
        if (inboxSignalPublisher == null) {
            return;
        }
        inboxSignalPublisher.signal(consumerName);
    }
}
