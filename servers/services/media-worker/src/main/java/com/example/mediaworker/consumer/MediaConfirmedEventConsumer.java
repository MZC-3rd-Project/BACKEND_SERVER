package com.example.mediaworker.consumer;

import com.example.event.consumer.ConsumerRoutingMode;
import com.example.event.consumer.RoutedEventConsumer;
import com.example.event.inbox.AbstractProcessorRoutingConsumer;
import com.example.event.inbox.InboxRoutingSupport;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Component
@RoutedEventConsumer(
        consumerName = MediaConfirmedEventProcessor.CONSUMER_NAME,
        defaultMode = ConsumerRoutingMode.INBOX
)
public class MediaConfirmedEventConsumer extends AbstractProcessorRoutingConsumer {

    public MediaConfirmedEventConsumer(
            InboxRoutingSupport inboxRoutingSupport,
            MediaConfirmedEventProcessor mediaConfirmedEventProcessor
    ) {
        super(inboxRoutingSupport, mediaConfirmedEventProcessor);
    }

    @KafkaListener(topics = "${media.worker.confirmed-topic:media.confirmed}", groupId = "${media.worker.group-id:media-worker-group}")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        consumeRecord(record);
    }
}
