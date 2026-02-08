package com.example.event.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = "com.example.event.outbox")
@EntityScan(basePackages = "com.example.event.outbox")
@EnableJpaRepositories(basePackages = "com.example.event.outbox")
public class OutboxAutoConfiguration {
}
