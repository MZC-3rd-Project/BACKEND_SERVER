package com.example.profile.service.command;

import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import com.example.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileProjectionSyncServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileImageRepository profileImageRepository;

    @Mock
    private ProfileMediaReferenceService profileMediaReferenceService;

    @Mock
    private EventPublisher eventPublisher;

    private ProfileProjectionSyncService profileProjectionSyncService;

    @BeforeEach
    void setUp() {
        profileProjectionSyncService = new ProfileProjectionSyncService(
                profileRepository,
                profileImageRepository,
                profileMediaReferenceService,
                eventPublisher
        );
    }

    @Test
    void upsertFromUserCreated_createsProfileAndDefaultImageWhenMissing() {
        Long userId = 501L;
        Profiles createdProfile = Profiles.createProjection(userId, "new@example.com", "newbie");

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileRepository.save(any(Profiles.class))).thenReturn(createdProfile);
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileImageRepository.save(any(ProfilesImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        profileProjectionSyncService.upsertFromUserCreated(userId, "new@example.com", "newbie");

        verify(profileRepository).save(any(Profiles.class));
        verify(profileImageRepository).save(any(ProfilesImage.class));
        verify(eventPublisher).publish(any(), any());
    }

    @Test
    void upsertFromUserCreated_updatesExistingProfileAndKeepsImageRow() {
        Long userId = 502L;
        Profiles existing = Profiles.builder()
                .id(9002L)
                .userId(userId)
                .email("old@example.com")
                .nickname("old-nick")
                .build();
        ProfilesImage existingImage = ProfilesImage.builder()
                .userId(userId)
                .profile(existing)
                .mediaId(100L)
                .build();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.of(existingImage));

        profileProjectionSyncService.upsertFromUserCreated(userId, "updated@example.com", "updated-nick");

        assertThat(existing.getEmail()).isEqualTo("updated@example.com");
        assertThat(existing.getNickname()).isEqualTo("updated-nick");
        verify(profileRepository, never()).save(any(Profiles.class));
        verify(profileImageRepository, never()).save(any(ProfilesImage.class));
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void applyUserEmailChanged_updatesEmailWhenProfileExists() {
        Long userId = 601L;
        Profiles existing = Profiles.builder()
                .id(9601L)
                .userId(userId)
                .email("old@example.com")
                .nickname("tester")
                .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        profileProjectionSyncService.applyUserEmailChanged(userId, "new@example.com");

        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        verify(profileRepository, never()).save(any(Profiles.class));
    }

    @Test
    void applyUserEmailChanged_noopWhenProfileMissing() {
        Long userId = 602L;
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        profileProjectionSyncService.applyUserEmailChanged(userId, "new@example.com");

        verify(profileRepository, never()).save(any(Profiles.class));
    }

    @Test
    void withdrawProjection_softDeletesProfileAndClearsMedia() {
        Long userId = 603L;
        Profiles existing = Profiles.builder()
                .id(9603L)
                .userId(userId)
                .email("user603@example.com")
                .nickname("user603")
                .build();
        ProfilesImage existingImage = ProfilesImage.builder()
                .userId(userId)
                .profile(existing)
                .mediaId(999L)
                .build();
        ReflectionTestUtils.setField(existing, "profileImage", existingImage);

        when(profileRepository.findByUserIdWithImage(userId)).thenReturn(Optional.of(existing));

        profileProjectionSyncService.withdrawProjection(userId);

        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(existingImage.getMediaId()).isNull();
        verify(profileMediaReferenceService).clearProfileLinks(userId);
    }

    @Test
    void withdrawProjection_whenProfileMissing_stillClearsMediaLinks() {
        Long userId = 604L;
        when(profileRepository.findByUserIdWithImage(userId)).thenReturn(Optional.empty());

        profileProjectionSyncService.withdrawProjection(userId);

        verify(profileMediaReferenceService).clearProfileLinks(userId);
    }
}
