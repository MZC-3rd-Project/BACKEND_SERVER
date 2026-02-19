package com.example.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
@EnableConfigurationProperties(NotificationEmailProperties.class)
public class EmailProviderConfig {

    @Bean
    public SesClient sesClient(NotificationEmailProperties properties) {
        return SesClient.builder()
                .region(Region.of(properties.getSesRegion()))
                .build();
    }
}
