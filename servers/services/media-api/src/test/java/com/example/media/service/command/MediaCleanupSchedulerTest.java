package com.example.media.service.command;

import com.example.media.service.metrics.MediaCleanupMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCleanupSchedulerTest {

    @Mock
    private MediaCommandService mediaCommandService;

    @Mock
    private MediaCleanupMetricsService mediaCleanupMetricsService;

    @InjectMocks
    private MediaCleanupScheduler mediaCleanupScheduler;

    @Test
    void expirePendingUploads_recordsCleanupMetrics() {
        when(mediaCommandService.expirePendingUploads())
                .thenReturn(new MediaCommandService.ExpireResult(3, 2, 1));

        mediaCleanupScheduler.expirePendingUploads();

        verify(mediaCommandService).expirePendingUploads();
        verify(mediaCleanupMetricsService).record(any(MediaCommandService.ExpireResult.class), any());
    }
}
