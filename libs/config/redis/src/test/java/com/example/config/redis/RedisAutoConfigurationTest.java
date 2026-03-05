package com.example.config.redis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void registersRedisTemplateAndCacheManager() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RedisConnectionFactory.class);
            assertThat(context).hasSingleBean(org.springframework.data.redis.core.RedisTemplate.class);
            assertThat(context).hasSingleBean(org.springframework.data.redis.cache.RedisCacheManager.class);
        });
    }
}
