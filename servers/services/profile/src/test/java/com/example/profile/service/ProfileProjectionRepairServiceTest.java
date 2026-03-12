package com.example.profile.service;

import com.example.clients.auth.facade.AuthItemQueryClientFacade;
import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.command.ProfileProjectionSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileProjectionRepairService 단위 테스트")
class ProfileProjectionRepairServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private AuthItemQueryClientFacade authItemQueryClientFacade;

    @Mock
    private ProfileProjectionSyncService profileProjectionSyncService;

    @InjectMocks
    private ProfileProjectionRepairService profileProjectionRepairService;

    @Test
    void ensureProfile_repairsMissingProjectionFromAuthSource() throws Exception {
        Long userId = 11L;
        Profiles repaired = Profiles.builder()
            .id(101L)
            .userId(userId)
            .email("seller@example.com")
            .nickname("seller")
            .build();

        when(profileRepository.findByUserId(userId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(repaired));
        when(authItemQueryClientFacade.findProfileInfo(userId))
            .thenReturn(objectMapper.readTree("""
                {
                  "userId": 11,
                  "email": "seller@example.com",
                  "nickname": "seller"
                }
                """));

        Optional<Profiles> result = profileProjectionRepairService.ensureProfile(userId);

        assertThat(result).contains(repaired);
        verify(profileProjectionSyncService).upsertFromUserCreated(userId, "seller@example.com", "seller");
    }

    @Test
    void ensureProfile_returnsEmptyWhenAuthSourceMissing() {
        Long userId = 12L;

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(authItemQueryClientFacade.findProfileInfo(userId)).thenReturn(null);

        Optional<Profiles> result = profileProjectionRepairService.ensureProfile(userId);

        assertThat(result).isEmpty();
    }
}
