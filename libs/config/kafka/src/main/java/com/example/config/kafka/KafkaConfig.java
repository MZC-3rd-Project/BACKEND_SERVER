package com.example.config.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.example.core.util.JsonUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.putIfAbsent(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.putIfAbsent(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.putIfAbsent(JsonDeserializer.TRUSTED_PACKAGES, "com.example.*,java.lang,java.util");
        props.putIfAbsent(JsonDeserializer.VALUE_DEFAULT_TYPE, Object.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler,
            KafkaProperties kafkaProperties) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setAutoStartup(kafkaProperties.getListener().isAutoStartup());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            DeadLetterMessageRepository deadLetterMessageRepository,
            KafkaConsumerProperties consumerProperties
    ) {
        int maxAttempts = Math.max(1, consumerProperties.getMaxRetryAttempts());
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxAttempts - 1);
        backOff.setInitialInterval(Math.max(100L, consumerProperties.getInitialBackoffMs()));
        backOff.setMultiplier(Math.max(1.0D, consumerProperties.getBackoffMultiplier()));
        backOff.setMaxInterval(Math.max(100L, consumerProperties.getMaxBackoffMs()));

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                deadLetterRecoverer(deadLetterMessageRepository, consumerProperties),
                backOff
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    private ConsumerRecordRecoverer deadLetterRecoverer(
            DeadLetterMessageRepository deadLetterMessageRepository,
            KafkaConsumerProperties consumerProperties
    ) {
        return (record, exception) -> {
            DeadLetterMessage deadLetterMessage = DeadLetterMessage.create(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    stringify(record.key()),
                    stringify(record.value()),
                    truncateError(exception, consumerProperties.getMaxErrorMessageLength()),
                    extractJsonField(record, "eventId"),
                    extractJsonField(record, "eventType")
            );
            try {
                deadLetterMessageRepository.save(deadLetterMessage);
                log.error("Kafka message moved to dead letter store: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), exception);
            } catch (DataAccessException dbEx) {
                log.error("Kafka dead letter store failed: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset(), dbEx);
                throw dbEx;
            }
        };
    }

    private String extractJsonField(ConsumerRecord<?, ?> record, String key) {
        String payload = stringify(record.value());
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payloadMap = JsonUtils.fromJson(payload, new TypeReference<>() {
            });
            Object value = payloadMap.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String truncateError(Throwable exception, int maxLength) {
        String message = exception == null ? "Kafka listener failed" : exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception == null ? "Kafka listener failed" : exception.getClass().getSimpleName();
        }
        int boundedLength = Math.max(64, maxLength);
        if (message.length() <= boundedLength) {
            return message;
        }
        return message.substring(0, boundedLength);
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
