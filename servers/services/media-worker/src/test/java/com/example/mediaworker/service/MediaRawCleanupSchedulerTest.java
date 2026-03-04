package com.example.mediaworker.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaRawCleanupSchedulerTest {

    @Mock
    private MediaRawCleanupService mediaRawCleanupService;

    @Test
    void cleanupRawAfterTransition_invokesCleanupService() {
        MediaRawCleanupScheduler scheduler = new MediaRawCleanupScheduler(mediaRawCleanupService);
        when(mediaRawCleanupService.cleanupRawAfterTransition())
                .thenReturn(new MediaRawCleanupService.CleanupResult(0, 0, 0, 0, 0, 0));

        scheduler.cleanupRawAfterTransition();

        verify(mediaRawCleanupService).cleanupRawAfterTransition();
    }
}
