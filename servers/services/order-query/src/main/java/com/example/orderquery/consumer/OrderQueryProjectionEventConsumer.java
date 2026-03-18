package com.example.orderquery.consumer;

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
        consumerName = OrderQueryProjectionEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class OrderQueryProjectionEventConsumer extends AbstractProcessorRoutingConsumer {

    public OrderQueryProjectionEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            OrderQueryProjectionEventProcessor orderQueryProjectionEventProcessor
    ) {
        super(inboxRoutingSupport, orderQueryProjectionEventProcessor);
    }

    @KafkaListener(
            topics = {"order-events", "item-events", "store-event"},
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
