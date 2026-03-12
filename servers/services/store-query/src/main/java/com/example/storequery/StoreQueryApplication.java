package com.example.storequery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example")
@EntityScan(basePackages = "com.example.storequery")
@EnableJpaRepositories(basePackages = "com.example.storequery")
public class StoreQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreQueryApplication.class, args);
    }
}
