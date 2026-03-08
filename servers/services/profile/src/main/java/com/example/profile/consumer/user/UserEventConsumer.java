package com.example.profile.consumer.user;

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
        consumerName = ProfileUserEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.DIRECT
)
public class UserEventConsumer extends AbstractProcessorRoutingConsumer {

    public UserEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            ProfileUserEventProcessor profileUserEventProcessor
    ) {
        super(inboxRoutingSupport, profileUserEventProcessor);
    }

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
