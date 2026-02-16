package com.example.config.kafka;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@EntityScan(basePackageClasses = {ProcessedEvent.class, DeadLetterMessage.class})
@EnableJpaRepositories(basePackageClasses = {ProcessedEventRepository.class, DeadLetterMessageRepository.class})
@Import(KafkaConfig.class)
public class KafkaAutoConfiguration {

    @Bean
    public IdempotentConsumerService idempotentConsumerService(ProcessedEventRepository processedEventRepository) {
        return new IdempotentConsumerService(processedEventRepository);
    }
}
