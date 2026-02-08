package com.example.config.kafka;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = "com.example.config.kafka")
@EntityScan(basePackages = "com.example.config.kafka")
@EnableJpaRepositories(basePackages = "com.example.config.kafka")
public class KafkaAutoConfiguration {
}
