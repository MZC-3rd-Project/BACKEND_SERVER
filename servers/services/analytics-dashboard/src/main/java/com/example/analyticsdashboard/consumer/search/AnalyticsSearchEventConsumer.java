package com.example.analyticsdashboard.consumer.search;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.analytics.search-consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
@RoutedEventConsumer(
        consumerName = AnalyticsSearchEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class AnalyticsSearchEventConsumer extends AbstractProcessorRoutingConsumer {

    public AnalyticsSearchEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            AnalyticsSearchEventProcessor analyticsSearchEventProcessor
    ) {
        super(inboxRoutingSupport, analyticsSearchEventProcessor);
    }

    @KafkaListener(topics = "search-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
