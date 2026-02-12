package com.example.hotdeal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.example")
@EnableAsync
@EnableScheduling
public class HotDealApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotDealApplication.class, args);
    }
}
