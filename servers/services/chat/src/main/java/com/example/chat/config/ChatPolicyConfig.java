package com.example.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        ChatRateLimitProperties.class,
        ChatRetentionProperties.class
})
public class ChatPolicyConfig {
}
