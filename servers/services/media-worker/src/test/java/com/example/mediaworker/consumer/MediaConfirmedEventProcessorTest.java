package com.example.mediaworker.consumer;

import com.example.config.kafka.IdempotentConsumerService;
import com.example.mediaworker.entity.MediaDerivativeProfile;
import com.example.mediaworker.entity.MediaDerivativeTask;
import com.example.mediaworker.service.MediaDerivativeTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaConfirmedEventProcessorTest {

    @Mock
    private MediaDerivativeTaskService mediaDerivativeTaskService;

    @Mock
    private IdempotentConsumerService idempotentConsumerService;

    @Mock
    private MediaDerivativeTask thumbnailTask;

    @Mock
    private MediaDerivativeTask displayTask;

    private MediaConfirmedEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MediaConfirmedEventProcessor(mediaDerivativeTaskService, idempotentConsumerService);
    }

    @Test
    void process_confirmedEvent_enqueuesDerivativeTasks() {
        stubIdempotentExecution("evt-media-1");
        when(mediaDerivativeTaskService.enqueuePending(101L, 3L, MediaDerivativeProfile.THUMBNAIL_WEBP, "evt-media-1"))
                .thenReturn(thumbnailTask);
        when(mediaDerivativeTaskService.enqueuePending(101L, 3L, MediaDerivativeProfile.DISPLAY_WEBP, "evt-media-1"))
                .thenReturn(displayTask);
        when(thumbnailTask.getId()).thenReturn(11L);
        when(displayTask.getId()).thenReturn(12L);
        when(thumbnailTask.getMediaVersion()).thenReturn(3L);

        String message = """
                {"eventId":"evt-media-1","eventType":"MEDIA_CONFIRMED","mediaId":101,"mediaVersion":3,"ownerType":"ITEM","ownerId":99,"usageType":"DETAIL","objectKey":"raw/item-101.png"}
                """;

        processor.process(message, "evt-media-1", "MEDIA_CONFIRMED");

        verify(mediaDerivativeTaskService).enqueuePending(101L, 3L, MediaDerivativeProfile.THUMBNAIL_WEBP, "evt-media-1");
        verify(mediaDerivativeTaskService).enqueuePending(101L, 3L, MediaDerivativeProfile.DISPLAY_WEBP, "evt-media-1");
    }

    @Test
    void process_normalizesInvalidMediaVersion() {
        stubIdempotentExecution("evt-media-2");
        when(mediaDerivativeTaskService.enqueuePending(202L, 1L, MediaDerivativeProfile.THUMBNAIL_WEBP, "evt-media-2"))
                .thenReturn(thumbnailTask);
        when(mediaDerivativeTaskService.enqueuePending(202L, 1L, MediaDerivativeProfile.DISPLAY_WEBP, "evt-media-2"))
                .thenReturn(displayTask);
        when(thumbnailTask.getId()).thenReturn(21L);
        when(displayTask.getId()).thenReturn(22L);
        when(thumbnailTask.getMediaVersion()).thenReturn(1L);

        String message = """
                {"eventId":"evt-media-2","eventType":"MEDIA_CONFIRMED","mediaId":202,"mediaVersion":0,"ownerType":"ITEM","ownerId":100}
                """;

        processor.process(message, "evt-media-2", "MEDIA_CONFIRMED");

        verify(mediaDerivativeTaskService).enqueuePending(202L, 1L, MediaDerivativeProfile.THUMBNAIL_WEBP, "evt-media-2");
        verify(mediaDerivativeTaskService).enqueuePending(202L, 1L, MediaDerivativeProfile.DISPLAY_WEBP, "evt-media-2");
    }

    @Test
    void process_invalidPayload_skipsProcessing() {
        String message = """
                {"eventId":"evt-media-3","eventType":"MEDIA_CONFIRMED"}
                """;

        processor.process(message, "evt-media-3", "MEDIA_CONFIRMED");

        verifyNoInteractions(idempotentConsumerService);
        verifyNoInteractions(mediaDerivativeTaskService);
    }

    @Test
    void process_rethrowsWhenIdempotentFails() {
        stubIdempotentFailure("evt-media-4", new RuntimeException("boom"));

        String message = """
                {"eventId":"evt-media-4","eventType":"MEDIA_CONFIRMED","mediaId":404,"mediaVersion":1}
                """;

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> processor.process(message, "evt-media-4", "MEDIA_CONFIRMED")
        );

        verify(mediaDerivativeTaskService, never()).enqueuePending(any(), any(), any(), any());
    }

    private void stubIdempotentExecution(String eventId) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq("MEDIA_CONFIRMED_EVENT"), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Supplier<Object> supplier = invocation.getArgument(2);
                    return Optional.ofNullable(supplier.get());
                });
    }

    private void stubIdempotentFailure(String eventId, RuntimeException exception) {
        when(idempotentConsumerService.executeIdempotent(eq(eventId), eq("MEDIA_CONFIRMED_EVENT"), any()))
                .thenThrow(exception);
    }
}
