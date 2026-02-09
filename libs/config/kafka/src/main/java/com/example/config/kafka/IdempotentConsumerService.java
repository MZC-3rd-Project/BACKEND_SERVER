package com.example.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentConsumerService {

    private final ProcessedEventRepository processedEventRepository;

    public boolean isProcessed(String eventId) {
        return processedEventRepository.findByEventId(eventId)
                .map(e -> e.getStatus() == ProcessedEvent.ProcessingStatus.PROCESSED)
                .orElse(false);
    }

    @Transactional
    public boolean markAsProcessing(String eventId, String eventType) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.debug("Event already processed or processing: {}", eventId);
            return false;
        }
        try {
            processedEventRepository.save(ProcessedEvent.create(eventId, eventType));
            processedEventRepository.flush();
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent duplicate event detected: {}", eventId);
            return false;
        }
    }

    @Transactional
    public void markAsProcessed(String eventId) {
        processedEventRepository.findByEventId(eventId)
                .ifPresent(ProcessedEvent::markAsProcessed);
    }

    @Transactional
    public void markAsFailed(String eventId, String errorMessage) {
        processedEventRepository.findByEventId(eventId)
                .ifPresent(e -> e.markAsFailed(errorMessage));
    }

    @Transactional
    public <T> Optional<T> executeIdempotent(String eventId, String eventType, Supplier<T> processor) {
        if (!markAsProcessing(eventId, eventType)) {
            log.info("Skipping duplicate event: {} ({})", eventId, eventType);
            return Optional.empty();
        }

        try {
            T result = processor.get();
            markAsProcessed(eventId);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            markAsFailed(eventId, e.getMessage());
            throw e;
        }
    }
}
