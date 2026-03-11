package com.example.analyticsdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.example")
public class AnalyticsDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsDashboardApplication.class, args);
    }
}
