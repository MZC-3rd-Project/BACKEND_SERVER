package com.example.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        NotificationDeliveryProperties.class,
        NotificationSseProperties.class
})
public class NotificationPropertiesConfig {
}
