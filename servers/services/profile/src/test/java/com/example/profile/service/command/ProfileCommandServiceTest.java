package com.example.profile.service.command;

import com.example.core.exception.BusinessException;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileCommandServiceTest {

    @Mock
    private ProfileImageRepository profileImageRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileAddressRepository profileAddressRepository;

    @Mock
    private ProfileMediaReferenceService profileMediaReferenceService;

    private ProfileCommandService profileCommandService;

    @BeforeEach
    void setUp() {
        profileCommandService = new ProfileCommandService(
            profileImageRepository,
            profileRepository,
            profileAddressRepository,
            profileMediaReferenceService
        );
    }

    @Test
    void updateProfile_updatesImageAndSyncsWhenMediaIdProvided() {
        Long userId = 101L;
        Profiles profile = createProfile(userId, "old@example.com", "oldNick");
        ProfilesImage image = ProfilesImage.builder()
            .userId(userId)
            .profile(profile)
            .mediaId(1L)
            .build();
        ProfileRequest request = ProfileRequest.builder()
            .mediaId(777L)
            .nickname("newNick")
            .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByNickname("newNick")).thenReturn(false);
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.of(image));
        when(profileMediaReferenceService.resolveCanonicalMediaId(777L, null)).thenReturn(777L);

        profileCommandService.updateProfile(request, userId);

        assertThat(image.getMediaId()).isEqualTo(777L);
        assertThat(profile.getNickname()).isEqualTo("newNick");
        verify(profileMediaReferenceService).validateReadableMedia(777L);
        verify(profileMediaReferenceService).syncProfileImageLink(userId, 777L);
    }

    @Test
    void updateProfile_clearsImageAndSyncsWhenMediaIdIsNull() {
        Long userId = 102L;
        Profiles profile = createProfile(userId, "user@example.com", "nick");
        ProfilesImage image = ProfilesImage.builder()
            .userId(userId)
            .profile(profile)
            .mediaId(55L)
            .build();
        ProfileRequest request = ProfileRequest.builder()
            .mediaId(null)
            .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.of(image));
        when(profileMediaReferenceService.resolveCanonicalMediaId(null, null)).thenReturn(null);

        profileCommandService.updateProfile(request, userId);

        assertThat(image.getMediaId()).isNull();
        verify(profileMediaReferenceService).validateReadableMedia(null);
        verify(profileMediaReferenceService).syncProfileImageLink(userId, null);
    }

    @Test
    void updateProfile_createsDefaultImageRowWhenMissing() {
        Long userId = 103L;
        Profiles profile = createProfile(userId, "user103@example.com", "nick103");
        ProfileRequest request = ProfileRequest.builder()
            .mediaId(888L)
            .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(profileImageRepository.save(any(ProfilesImage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(profileMediaReferenceService.resolveCanonicalMediaId(888L, null)).thenReturn(888L);

        profileCommandService.updateProfile(request, userId);

        verify(profileImageRepository).save(any(ProfilesImage.class));
        verify(profileMediaReferenceService).syncProfileImageLink(userId, 888L);
    }

    @Test
    void updateProfile_passesLegacyMediaRefToConverter() {
        Long userId = 105L;
        Profiles profile = createProfile(userId, "user105@example.com", "nick105");
        ProfileRequest request = ProfileRequest.builder()
            .mediaRef("media-4321")
            .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.of(
            ProfilesImage.builder().userId(userId).profile(profile).mediaId(null).build()
        ));
        when(profileMediaReferenceService.resolveCanonicalMediaId(null, "media-4321")).thenReturn(4321L);

        profileCommandService.updateProfile(request, userId);

        verify(profileMediaReferenceService).resolveCanonicalMediaId(null, "media-4321");
        verify(profileMediaReferenceService).syncProfileImageLink(userId, 4321L);
    }

    @Test
    void updateProfile_throwsWhenNicknameAlreadyExistsForAnotherUser() {
        Long userId = 104L;
        Profiles profile = createProfile(userId, "user104@example.com", "currentNick");
        ProfileRequest request = ProfileRequest.builder()
            .nickname("takenNick")
            .build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(profileImageRepository.findByUserId(userId)).thenReturn(Optional.of(
            ProfilesImage.builder().userId(userId).profile(profile).mediaId(null).build()
        ));
        when(profileMediaReferenceService.resolveCanonicalMediaId(null, null)).thenReturn(null);
        when(profileRepository.existsByNickname("takenNick")).thenReturn(true);

        assertThatThrownBy(() -> profileCommandService.updateProfile(request, userId))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ProfileErrorCode.PROFILE_ALREADY_NICKNAME);

        verify(profileMediaReferenceService, never()).syncProfileImageLink(eq(userId), any());
    }

    private Profiles createProfile(Long userId, String email, String nickname) {
        return Profiles.builder()
            .id(userId + 1000L)
            .userId(userId)
            .email(email)
            .nickname(nickname)
            .phoneNumber("010-0000-0000")
            .build();
    }
}
