package com.example.event.consumer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(EventConsumerRoutingProperties.class)
public class EventConsumerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventConsumerRoutingResolver eventConsumerRoutingResolver(
            EventConsumerRoutingProperties routingProperties
    ) {
        return new EventConsumerRoutingResolver(routingProperties);
    }
}
