package com.example.config.kafka;

import com.example.event.EventPublisher;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@EnableConfigurationProperties({KafkaConsumerProperties.class, DeadLetterProperties.class})
@EntityScan(basePackageClasses = {ProcessedEvent.class, DeadLetterMessage.class})
@EnableJpaRepositories(basePackageClasses = {ProcessedEventRepository.class, DeadLetterMessageRepository.class})
@Import(KafkaConfig.class)
public class KafkaAutoConfiguration {

    @Bean
    public IdempotentConsumerService idempotentConsumerService(ProcessedEventRepository processedEventRepository) {
        return new IdempotentConsumerService(processedEventRepository);
    }

    @Bean
    public DeadLetterStoreService deadLetterStoreService(
            DeadLetterMessageRepository deadLetterMessageRepository,
            DeadLetterAlertPublisher deadLetterAlertPublisher,
            DeadLetterProperties deadLetterProperties
    ) {
        return new DeadLetterStoreService(
                deadLetterMessageRepository,
                deadLetterAlertPublisher,
                deadLetterProperties
        );
    }

    @Bean
    public DeadLetterAlertPublisher deadLetterAlertPublisher(
            ObjectProvider<EventPublisher> eventPublisherProvider,
            Environment environment
    ) {
        return new DeadLetterAlertPublisher(eventPublisherProvider, environment);
    }

    @Bean
    public DeadLetterRecoveryService deadLetterRecoveryService(
            DeadLetterMessageRepository deadLetterMessageRepository,
            ProcessedEventRepository processedEventRepository,
            org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate,
            DeadLetterProperties deadLetterProperties,
            DeadLetterAlertPublisher deadLetterAlertPublisher
    ) {
        return new DeadLetterRecoveryService(
                deadLetterMessageRepository,
                processedEventRepository,
                kafkaTemplate,
                deadLetterProperties,
                deadLetterAlertPublisher
        );
    }

    @Bean
    public DeadLetterRetryScheduler deadLetterRetryScheduler(DeadLetterRecoveryService deadLetterRecoveryService) {
        return new DeadLetterRetryScheduler(deadLetterRecoveryService);
    }
}
