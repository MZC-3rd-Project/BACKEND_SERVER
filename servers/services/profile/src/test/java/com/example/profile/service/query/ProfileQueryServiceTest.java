package com.example.profile.service.query;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.ProfileProjectionRepairService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileQueryService 단위 테스트")
class ProfileQueryServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileAddressRepository profileAddressRepository;

    @Mock
    private ProfileProjectionRepairService profileProjectionRepairService;

    @Mock
    private MediaClientFacade mediaClientFacade;

    @InjectMocks
    private ProfileQueryService profileQueryService;

    @Test
    @DisplayName("getProfileSnapshot() 은 nickname 과 profile image mediaId 를 반환한다")
    void getProfileSnapshot_returns_profile_snapshot() {
        Profiles profile = Profiles.builder()
            .userId(101L)
            .email("seller@example.com")
            .nickname("seller")
            .build();
        ProfilesImage profileImage = ProfilesImage.builder()
            .userId(101L)
            .mediaId(999L)
            .build();
        ReflectionTestUtils.setField(profile, "profileImage", profileImage);

        given(profileProjectionRepairService.ensureProfileWithImage(101L)).willReturn(Optional.of(profile));

        var result = profileQueryService.getProfileSnapshot(101L);

        assertThat(result.userId()).isEqualTo(101L);
        assertThat(result.nickname()).isEqualTo("seller");
        assertThat(result.profileImageMediaId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("getProfile() 은 mediaId 와 mediaUrl 을 함께 반환한다")
    void getProfile_returns_media_url() {
        Profiles profile = Profiles.builder()
            .userId(101L)
            .email("seller@example.com")
            .nickname("seller")
            .build();
        ProfilesImage profileImage = ProfilesImage.builder()
            .userId(101L)
            .mediaId(999L)
            .build();
        ReflectionTestUtils.setField(profile, "profileImage", profileImage);

        given(profileProjectionRepairService.ensureProfileWithImage(101L)).willReturn(Optional.of(profile));
        given(mediaClientFacade.getMediaUrl(999L)).willReturn("https://cdn.example.com/profile-999.webp");

        var result = profileQueryService.getProfile(101L);

        assertThat(result.getMediaId()).isEqualTo(999L);
        assertThat(result.getMediaUrl()).isEqualTo("https://cdn.example.com/profile-999.webp");
    }

    @Test
    @DisplayName("getProfile() 은 media url 해석 실패 시 mediaId 는 유지하고 mediaUrl 은 null 로 반환한다")
    void getProfile_returns_null_media_url_when_resolution_fails() {
        Profiles profile = Profiles.builder()
            .userId(101L)
            .email("seller@example.com")
            .nickname("seller")
            .build();
        ProfilesImage profileImage = ProfilesImage.builder()
            .userId(101L)
            .mediaId(999L)
            .build();
        ReflectionTestUtils.setField(profile, "profileImage", profileImage);

        given(profileProjectionRepairService.ensureProfileWithImage(101L)).willReturn(Optional.of(profile));
        given(mediaClientFacade.getMediaUrl(999L)).willThrow(new IllegalStateException("media down"));

        var result = profileQueryService.getProfile(101L);

        assertThat(result.getMediaId()).isEqualTo(999L);
        assertThat(result.getMediaUrl()).isNull();
    }
}
