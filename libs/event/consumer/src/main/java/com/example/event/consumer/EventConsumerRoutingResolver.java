package com.example.event.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;

@RequiredArgsConstructor
public class EventConsumerRoutingResolver {

    private final EventConsumerRoutingProperties routingProperties;

    public ConsumerRoutingMode resolveMode(Class<?> consumerType) {
        RoutedEventConsumer metadata = getMetadata(consumerType);
        EventConsumerRoutingProperties.RoutingProperties override =
                routingProperties.getRouting().get(metadata.consumerName());
        if (override != null && override.getMode() != null) {
            return override.getMode();
        }
        return metadata.defaultMode();
    }

    public boolean isInbox(Class<?> consumerType) {
        return resolveMode(consumerType) == ConsumerRoutingMode.INBOX;
    }

    public String consumerName(Class<?> consumerType) {
        return getMetadata(consumerType).consumerName();
    }

    private RoutedEventConsumer getMetadata(Class<?> consumerType) {
        RoutedEventConsumer metadata = AnnotatedElementUtils.findMergedAnnotation(consumerType, RoutedEventConsumer.class);
        if (metadata == null) {
            throw new IllegalStateException("Missing @RoutedEventConsumer on " + consumerType.getName());
        }
        return metadata;
    }
}
