package com.example.notification.config;

import com.example.notification.service.realtime.SseRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class SseRedisConfig {

    @Bean
    public ChannelTopic sseNotificationTopic(
            @Value("${notification.sse.topic:sse-notifications}") String topicName) {
        return new ChannelTopic(topicName);
    }

    @Bean
    public RedisMessageListenerContainer sseMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChannelTopic sseNotificationTopic,
            SseRedisSubscriber sseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(sseRedisSubscriber, sseNotificationTopic);
        return container;
    }
}
