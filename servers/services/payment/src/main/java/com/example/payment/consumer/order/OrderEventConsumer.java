package com.example.payment.consumer.order;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RoutedEventConsumer(
        consumerName = PaymentOrderEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class OrderEventConsumer extends AbstractProcessorRoutingConsumer {

    public OrderEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            PaymentOrderEventProcessor paymentOrderEventProcessor
    ) {
        super(inboxRoutingSupport, paymentOrderEventProcessor);
    }

    @KafkaListener(topics = "order-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
