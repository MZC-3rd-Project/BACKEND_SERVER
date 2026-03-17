package com.example.analyticsdashboard.consumer.search;

import com.example.analyticsdashboard.service.ingest.AnalyticsEventIngestService;
import com.example.config.kafka.IdempotentConsumerService;
import com.example.event.consumer.EventConsumerRoutingProperties;
import com.example.event.consumer.EventConsumerRoutingResolver;
import com.example.event.inbox.InboxEnqueueService;
import com.example.event.inbox.InboxRoutingSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AnalyticsSearchConsumerConditionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchConsumerTestConfiguration.class);

    @Test
    void registersSearchConsumerBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AnalyticsSearchEventProcessor.class);
            assertThat(context).hasSingleBean(AnalyticsSearchEventConsumer.class);
        });
    }

    @Test
    void skipsSearchConsumerBeansWhenDisabled() {
        contextRunner
                .withPropertyValues("app.analytics.search-consumer.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AnalyticsSearchEventProcessor.class);
                    assertThat(context).doesNotHaveBean(AnalyticsSearchEventConsumer.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AnalyticsSearchEventProcessor.class, AnalyticsSearchEventConsumer.class})
    static class SearchConsumerTestConfiguration {

        @Bean
        EventConsumerRoutingProperties eventConsumerRoutingProperties() {
            return new EventConsumerRoutingProperties();
        }

        @Bean
        InboxRoutingSupport inboxRoutingSupport(EventConsumerRoutingProperties routingProperties) {
            return new InboxRoutingSupport(
                    mock(InboxEnqueueService.class),
                    new EventConsumerRoutingResolver(routingProperties)
            );
        }

        @Bean
        IdempotentConsumerService idempotentConsumerService() {
            return mock(IdempotentConsumerService.class);
        }

        @Bean
        AnalyticsEventIngestService analyticsEventIngestService() {
            return mock(AnalyticsEventIngestService.class);
        }

    }
}
