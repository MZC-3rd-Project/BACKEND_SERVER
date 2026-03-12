package com.example.profile.service.query;

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
}
