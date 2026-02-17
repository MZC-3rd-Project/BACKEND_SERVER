package com.example.config.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
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
        Optional<ProcessedEvent> existing = processedEventRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            ProcessedEvent.ProcessingStatus status = existing.get().getStatus();
            if (status == ProcessedEvent.ProcessingStatus.PROCESSED
                    || status == ProcessedEvent.ProcessingStatus.PROCESSING) {
                log.debug("Event already processed or processing: {}", eventId);
                return false;
            }

            if (status == ProcessedEvent.ProcessingStatus.FAILED) {
                int updated = processedEventRepository.updateStatusIfCurrent(
                        eventId,
                        ProcessedEvent.ProcessingStatus.FAILED,
                        ProcessedEvent.ProcessingStatus.PROCESSING
                );
                if (updated > 0) {
                    log.info("Retrying previously failed event: {}", eventId);
                    return true;
                }
                log.debug("Concurrent retry claim failed: {}", eventId);
                return false;
            }
        }

        try {
            processedEventRepository.save(ProcessedEvent.create(eventId, eventType));
            processedEventRepository.flush();
            return true;
        } catch (DataIntegrityViolationException e) {
            int updated = processedEventRepository.updateStatusIfCurrent(
                    eventId,
                    ProcessedEvent.ProcessingStatus.FAILED,
                    ProcessedEvent.ProcessingStatus.PROCESSING
            );
            if (updated > 0) {
                log.info("Retrying previously failed event after duplicate insert race: {}", eventId);
                return true;
            }
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
