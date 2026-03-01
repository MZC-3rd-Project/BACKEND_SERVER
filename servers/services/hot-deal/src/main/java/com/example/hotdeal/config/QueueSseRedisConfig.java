package com.example.hotdeal.config;

import com.example.hotdeal.service.QueueSseRedisSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class QueueSseRedisConfig {

    @Bean
    public ChannelTopic queueSseTopic(
            @Value("${hotdeal.queue.sse.topic:hotdeal-queue-sse-events}") String topicName) {
        return new ChannelTopic(topicName);
    }

    @Bean
    public RedisMessageListenerContainer queueSseMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChannelTopic queueSseTopic,
            QueueSseRedisSubscriber queueSseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(queueSseRedisSubscriber, queueSseTopic);
        return container;
    }
}
