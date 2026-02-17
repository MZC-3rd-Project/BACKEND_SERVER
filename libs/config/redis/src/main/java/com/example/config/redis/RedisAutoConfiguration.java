package com.example.config.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(RedisConfig.class)
public class RedisAutoConfiguration {
}
