package com.example.storequery.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.storequery.service.StoreReadModelProjectionApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreQueryProjectionEventProcessorTest {

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private StoreReadModelProjectionApplicationService projectionApplicationService;

    @Test
    void process_projectsSupportedEventThroughApplicationService() {
        StoreQueryProjectionEventProcessor processor = new StoreQueryProjectionEventProcessor(
            idempotentConsumerService,
            projectionApplicationService
        );
        String message = "{\"eventId\":\"evt-1\",\"eventType\":\"ITEM_UPDATED\",\"storeId\":101}";

        when(idempotentConsumerService.executeIdempotent(eq("evt-1"), eq("STORE_QUERY_PROJECTION_EVENT"), any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Supplier<Void> supplier = invocation.getArgument(2);
                supplier.get();
                return Optional.empty();
            });

        processor.process(message, "evt-1", "ITEM_UPDATED");

        verify(projectionApplicationService).projectByTrigger("ITEM_UPDATED", null, message);
    }
}
