package com.example.config.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentConsumerServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private IdempotentConsumerService service;

    @Test
    void markAsProcessing_createsNewRecordWhenEventIsFirstSeen() {
        when(processedEventRepository.findByEventId("evt-1")).thenReturn(Optional.empty());

        boolean result = service.markAsProcessing("evt-1", "PAYMENT_EVENT");

        assertTrue(result);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(processedEventRepository).flush();
    }

    @Test
    void markAsProcessing_returnsFalseForAlreadyProcessedEvent() {
        ProcessedEvent processed = ProcessedEvent.create("evt-1", "PAYMENT_EVENT");
        processed.markAsProcessed();
        when(processedEventRepository.findByEventId("evt-1")).thenReturn(Optional.of(processed));

        boolean result = service.markAsProcessing("evt-1", "PAYMENT_EVENT");

        assertFalse(result);
    }

    @Test
    void markAsProcessing_retriesFailedEventWithCasUpdate() {
        ProcessedEvent failed = ProcessedEvent.create("evt-1", "PAYMENT_EVENT");
        failed.markAsFailed("temporary db lock");
        when(processedEventRepository.findByEventId("evt-1")).thenReturn(Optional.of(failed));
        when(processedEventRepository.updateStatusIfCurrent(
                "evt-1",
                ProcessedEvent.ProcessingStatus.FAILED,
                ProcessedEvent.ProcessingStatus.PROCESSING
        )).thenReturn(1);

        boolean result = service.markAsProcessing("evt-1", "PAYMENT_EVENT");

        assertTrue(result);
    }

    @Test
    void markAsProcessing_retriesFailedEventAfterInsertRace() {
        when(processedEventRepository.findByEventId("evt-1")).thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(processedEventRepository).save(any(ProcessedEvent.class));
        when(processedEventRepository.updateStatusIfCurrent(
                "evt-1",
                ProcessedEvent.ProcessingStatus.FAILED,
                ProcessedEvent.ProcessingStatus.PROCESSING
        )).thenReturn(1);

        boolean result = service.markAsProcessing("evt-1", "PAYMENT_EVENT");

        assertTrue(result);
    }
}
