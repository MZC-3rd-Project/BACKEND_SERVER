package com.example.event.inbox;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.AbstractIdempotentRoutingJsonMessageConsumer;
import com.example.event.consumer.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public abstract class AbstractInboxAwareRoutingJsonMessageConsumer<T extends EventEnvelope>
        extends AbstractIdempotentRoutingJsonMessageConsumer<T> {

    private final InboxRoutingSupport inboxRoutingSupport;

    protected AbstractInboxAwareRoutingJsonMessageConsumer(
            IdempotentConsumerService idempotentConsumerService,
            InboxRoutingSupport inboxRoutingSupport
    ) {
        super(idempotentConsumerService);
        this.inboxRoutingSupport = inboxRoutingSupport;
    }

    protected final void consumeRoutedRecord(ConsumerRecord<String, Object> record) {
        consumeRecord(
                record,
                inboxRoutingSupport.isInbox(getClass()),
                (envelope, message) -> inboxRoutingSupport.enqueue(getClass(), envelope, message)
        );
    }
}
