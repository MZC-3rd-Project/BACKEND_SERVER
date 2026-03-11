package com.example.profile.controller.command;

import com.example.api.response.ApiResponse;
import com.example.profile.dto.request.InternalProfileCreateRequest;
import com.example.profile.service.command.ProfileProjectionSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfileInternalCommandControllerTest {

    @Mock
    private ProfileProjectionSyncService profileProjectionSyncService;

    @InjectMocks
    private ProfileInternalCommandController controller;

    @Test
    void createProfile_delegatesToProjectionSyncService() {
        InternalProfileCreateRequest request = new InternalProfileCreateRequest(
                4001L, "user4001@example.com", "user4001"
        );

        ApiResponse<Void> response = controller.createProfile(request);

        assertThat(response.isSuccess()).isTrue();
        verify(profileProjectionSyncService).upsertFromUserCreated(
                4001L, "user4001@example.com", "user4001"
        );
    }
}
