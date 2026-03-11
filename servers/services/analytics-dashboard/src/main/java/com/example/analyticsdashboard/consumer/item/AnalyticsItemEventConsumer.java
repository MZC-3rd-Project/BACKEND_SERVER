package com.example.analyticsdashboard.consumer.item;

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
        consumerName = AnalyticsItemEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class AnalyticsItemEventConsumer extends AbstractProcessorRoutingConsumer {

    public AnalyticsItemEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            AnalyticsItemEventProcessor analyticsItemEventProcessor
    ) {
        super(inboxRoutingSupport, analyticsItemEventProcessor);
    }

    @KafkaListener(topics = "item-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
