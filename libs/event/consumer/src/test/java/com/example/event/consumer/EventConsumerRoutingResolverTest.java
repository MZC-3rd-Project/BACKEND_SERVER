package com.example.event.consumer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventConsumerRoutingResolverTest {

    @Test
    void resolveMode_usesAnnotationDefaultWhenOverrideMissing() {
        EventConsumerRoutingResolver resolver = new EventConsumerRoutingResolver(new EventConsumerRoutingProperties());

        ConsumerRoutingMode mode = resolver.resolveMode(SampleConsumer.class);

        assertThat(mode).isEqualTo(ConsumerRoutingMode.INBOX);
        assertThat(resolver.consumerName(SampleConsumer.class)).isEqualTo("sample-consumer");
    }

    @Test
    void resolveMode_prefersConfiguredOverride() {
        EventConsumerRoutingProperties properties = new EventConsumerRoutingProperties();
        EventConsumerRoutingProperties.RoutingProperties routing = new EventConsumerRoutingProperties.RoutingProperties();
        routing.setMode(ConsumerRoutingMode.DIRECT);
        properties.getRouting().put("sample-consumer", routing);
        EventConsumerRoutingResolver resolver = new EventConsumerRoutingResolver(properties);

        ConsumerRoutingMode mode = resolver.resolveMode(SampleConsumer.class);

        assertThat(mode).isEqualTo(ConsumerRoutingMode.DIRECT);
    }

    @RoutedEventConsumer(consumerName = "sample-consumer", defaultMode = ConsumerRoutingMode.INBOX)
    private static final class SampleConsumer {
    }
}
