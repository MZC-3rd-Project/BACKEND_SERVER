package com.example.mediaworker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaDerivativeCleanupSchedulerTest {

    @Mock
    private MediaDerivativeCleanupService mediaDerivativeCleanupService;

    @Test
    void cleanupOrphanedDerivatives_invokesCleanupService() {
        MediaDerivativeCleanupScheduler scheduler = new MediaDerivativeCleanupScheduler(mediaDerivativeCleanupService);
        when(mediaDerivativeCleanupService.cleanupOrphanedDerivatives())
                .thenReturn(new MediaDerivativeCleanupService.CleanupResult(0, 0, 0, 0));

        scheduler.cleanupOrphanedDerivatives();

        verify(mediaDerivativeCleanupService).cleanupOrphanedDerivatives();
    }
}
