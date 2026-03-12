package com.example.storequery.consumer;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RoutedEventConsumer(
    consumerName = StoreQueryProjectionEventProcessor.CONSUMER_NAME,
    defaultMode = ConsumerRoutingMode.INBOX
)
public class StoreQueryProjectionEventConsumer extends AbstractProcessorRoutingConsumer {

    public StoreQueryProjectionEventConsumer(
        InboxRoutingSupport inboxRoutingSupport,
        StoreQueryProjectionEventProcessor storeQueryProjectionEventProcessor
    ) {
        super(inboxRoutingSupport, storeQueryProjectionEventProcessor);
    }

    @KafkaListener(
        topics = {"store-event", "store-events", "item-events", "user-events", "profile-events"},
        groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
