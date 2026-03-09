package com.example.event.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EventConsumerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventConsumerAutoConfiguration.class));

    @Test
    void registersRoutingInfrastructureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(EventConsumerRoutingProperties.class);
            assertThat(context).hasSingleBean(EventConsumerRoutingResolver.class);
        });
    }

    @Test
    void bindsRoutingOverridesFromProperties() {
        contextRunner
                .withPropertyValues("app.event.consumer.routing.sample-consumer.mode=INBOX")
                .run(context -> {
                    EventConsumerRoutingProperties properties = context.getBean(EventConsumerRoutingProperties.class);

                    assertThat(properties.getRouting())
                            .containsKey("sample-consumer");
                    assertThat(properties.getRouting().get("sample-consumer").getMode())
                            .isEqualTo(ConsumerRoutingMode.INBOX);
                });
    }
}
