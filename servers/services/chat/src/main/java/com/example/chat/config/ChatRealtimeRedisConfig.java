package com.example.chat.config;

import com.example.chat.service.realtime.ChatRealtimeRedisSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@EnableConfigurationProperties(ChatRealtimeProperties.class)
public class ChatRealtimeRedisConfig {

    @Bean
    public ChannelTopic chatRoomRealtimeTopic(ChatRealtimeProperties properties) {
        return new ChannelTopic(properties.getRedisTopic());
    }

    @Bean
    @ConditionalOnProperty(prefix = "chat.realtime", name = "redis-fanout-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer chatRealtimeMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChannelTopic chatRoomRealtimeTopic,
            ChatRealtimeRedisSubscriber chatRealtimeRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(chatRealtimeRedisSubscriber, chatRoomRealtimeTopic);
        return container;
    }
}
