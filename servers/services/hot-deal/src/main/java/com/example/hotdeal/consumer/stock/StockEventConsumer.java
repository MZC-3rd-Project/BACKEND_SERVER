package com.example.hotdeal.consumer.stock;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RoutedEventConsumer(
        consumerName = StockEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class StockEventConsumer extends AbstractProcessorRoutingConsumer {

    public StockEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            StockEventProcessor stockEventProcessor
    ) {
        super(inboxRoutingSupport, stockEventProcessor);
    }

    @KafkaListener(topics = "stock-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
