package com.example.config.lock.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonLockAutoConfigurationTest {

    @Test
    void shouldApplySslUsernamePasswordAndDatabase() {
        Config config = new Config();
        SingleServerConfig singleServer = RedissonLockAutoConfiguration.configureSingleServer(
                config,
                "redis.example.local",
                6380,
                "svc-user",
                "svc-password",
                2,
                true
        );

        assertThat(singleServer.getAddress()).isEqualTo("rediss://redis.example.local:6380");
        assertThat(singleServer.getUsername()).isEqualTo("svc-user");
        assertThat(singleServer.getPassword()).isEqualTo("svc-password");
        assertThat(singleServer.getDatabase()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreBlankCredentialsAndClampNegativeDatabase() {
        Config config = new Config();
        SingleServerConfig singleServer = RedissonLockAutoConfiguration.configureSingleServer(
                config,
                "localhost",
                6379,
                " ",
                "",
                -1,
                false
        );

        assertThat(singleServer.getAddress()).isEqualTo("redis://localhost:6379");
        assertThat(singleServer.getUsername()).isNull();
        assertThat(singleServer.getPassword()).isNull();
        assertThat(singleServer.getDatabase()).isZero();
    }
}
