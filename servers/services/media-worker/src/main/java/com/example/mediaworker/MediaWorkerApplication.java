package com.example.mediaworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.example.mediaworker", "com.example.api.handler"})
@EntityScan(basePackages = "com.example.mediaworker")
@EnableJpaRepositories(basePackages = "com.example.mediaworker")
public class MediaWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaWorkerApplication.class, args);
    }
}
