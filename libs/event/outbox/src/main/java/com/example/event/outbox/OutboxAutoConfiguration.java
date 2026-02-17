package com.example.event.outbox;

import com.example.event.EventPublisher;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
@EntityScan(basePackageClasses = OutboxMessage.class)
@EnableJpaRepositories(basePackageClasses = OutboxRepository.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(
            OutboxRepository outboxRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return new OutboxService(outboxRepository, applicationEventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher eventPublisher(OutboxService outboxService) {
        return outboxService;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.outbox", name = "immediate-publish-enabled", havingValue = "true", matchIfMissing = true)
    public ImmediatePublisher immediatePublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            OutboxRepository outboxRepository
    ) {
        return new ImmediatePublisher(kafkaTemplate, outboxRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            OutboxProperties outboxProperties
    ) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate, transactionTemplate, outboxProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.outbox.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxCleanupScheduler outboxCleanupScheduler(
            OutboxRepository outboxRepository,
            OutboxProperties outboxProperties
    ) {
        return new OutboxCleanupScheduler(outboxRepository, outboxProperties);
    }
}
