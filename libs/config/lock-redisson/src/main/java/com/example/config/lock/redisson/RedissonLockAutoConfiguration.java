package com.example.config.lock.redisson;

import com.example.config.lock.DistributedLockExecutor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class RedissonLockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled
    ) {
        return Redisson.create(createConfig(host, port, username, password, database, sslEnabled));
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLockExecutor.class)
    public DistributedLockExecutor distributedLockExecutor(RedissonClient redissonClient) {
        return new RedissonDistributedLockExecutor(redissonClient);
    }

    static Config createConfig(
            String host,
            int port,
            String username,
            String password,
            int database,
            boolean sslEnabled
    ) {
        Config config = new Config();
        configureSingleServer(config, host, port, username, password, database, sslEnabled);
        return config;
    }

    static SingleServerConfig configureSingleServer(
            Config config,
            String host,
            int port,
            String username,
            String password,
            int database,
            boolean sslEnabled
    ) {
        String protocol = sslEnabled ? "rediss://" : "redis://";
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(protocol + host + ":" + port)
                .setDatabase(Math.max(0, database));

        if (username != null && !username.isBlank()) {
            singleServer.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            singleServer.setPassword(password);
        }
        return singleServer;
    }
}
