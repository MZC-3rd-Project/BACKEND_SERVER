package com.example.product.consumer.funding;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RoutedEventConsumer(
        consumerName = FundingEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class FundingEventConsumer extends AbstractProcessorRoutingConsumer {

    public FundingEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            FundingEventProcessor fundingEventProcessor
    ) {
        super(inboxRoutingSupport, fundingEventProcessor);
    }

    @KafkaListener(topics = "funding-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
