package com.example.core.id;

import com.example.core.id.jpa.SnowflakeIdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Snowflake ID 생성기 자동 설정.
 *
 * application.yml에서 datacenter/worker ID를 설정할 수 있다:
 *
 * app:
 *   snowflake:
 *     datacenter-id: 1
 *     worker-id: 1
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SnowflakeProperties.class)
@RequiredArgsConstructor
public class SnowflakeAutoConfiguration {

    private final SnowflakeProperties properties;

    @Bean
    public Snowflake snowflake() {
        Snowflake snowflake = new Snowflake(properties.getDatacenterId(), properties.getWorkerId());
        SnowflakeIdentifierGenerator.setInstance(snowflake);
        log.info("[Snowflake] Initialized: datacenterId={}, workerId={}",
                properties.getDatacenterId(), properties.getWorkerId());
        return snowflake;
    }
}
