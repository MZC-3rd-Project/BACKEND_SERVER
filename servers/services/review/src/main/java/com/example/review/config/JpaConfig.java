package com.example.review.config;

import com.example.review.domain.ReviewRepository;
import com.example.review.repository.ReviewMediaLinkSyncTaskRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackageClasses = {
        ReviewRepository.class,
        ReviewMediaLinkSyncTaskRepository.class
})
public class JpaConfig {
}
