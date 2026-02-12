package com.example.hotdeal.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackages = "com.example.hotdeal.repository")
public class JpaConfig {
}
