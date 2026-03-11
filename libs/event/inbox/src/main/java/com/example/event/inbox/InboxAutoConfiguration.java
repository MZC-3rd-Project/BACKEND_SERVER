package com.example.event.inbox;

import com.example.event.consumer.EventConsumerAutoConfiguration;
import com.example.event.consumer.EventMessageProcessor;
import com.example.event.consumer.EventConsumerRoutingResolver;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(EventConsumerAutoConfiguration.class)
@ConditionalOnClass(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "app.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InboxProperties.class)
@EntityScan(basePackageClasses = InboxMessage.class)
@EnableJpaRepositories(basePackageClasses = InboxRepository.class)
public class InboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InboxHandlerRegistry inboxHandlerRegistry(
            ObjectProvider<List<InboxEventHandler>> handlerProvider,
            ObjectProvider<List<EventMessageProcessor>> processorProvider
    ) {
        List<InboxEventHandler> handlers = handlerProvider.getIfAvailable(List::of);
        List<EventMessageProcessor> processors = processorProvider.getIfAvailable(List::of);
        return new InboxHandlerRegistry(InboxHandlerAdapters.merge(handlers, processors));
    }

    @Bean
    @ConditionalOnMissingBean(name = "inboxTaskExecutor")
    public TaskExecutor inboxTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("inbox-trigger-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "app.inbox.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public InboxWorkerScheduler inboxWorkerScheduler(
            InboxRepository inboxRepository,
            InboxHandlerRegistry inboxHandlerRegistry,
            InboxProperties inboxProperties,
            TaskExecutor inboxTaskExecutor,
            TransactionTemplate transactionTemplate
    ) {
        return new InboxWorkerScheduler(
                inboxRepository,
                inboxHandlerRegistry,
                inboxProperties,
                inboxTaskExecutor,
                transactionTemplate
        );
    }

    @Bean
    @ConditionalOnMissingBean(InboxSignalPublisher.class)
    public InboxSignalPublisher inboxSignalPublisher() {
        return consumerName -> {
            // no-op fallback when worker is disabled
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxEnqueueService inboxEnqueueService(
            InboxRepository inboxRepository,
            ObjectProvider<InboxSignalPublisher> inboxSignalPublisherProvider,
            InboxProperties inboxProperties
    ) {
        return new InboxEnqueueService(inboxRepository, inboxSignalPublisherProvider, inboxProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public InboxRoutingSupport inboxRoutingSupport(
            InboxEnqueueService inboxEnqueueService,
            EventConsumerRoutingResolver eventConsumerRoutingResolver
    ) {
        return new InboxRoutingSupport(inboxEnqueueService, eventConsumerRoutingResolver);
    }
}
