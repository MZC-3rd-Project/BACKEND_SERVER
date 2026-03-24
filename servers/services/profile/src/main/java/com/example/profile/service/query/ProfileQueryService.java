package com.example.profile.service.query;

import com.example.core.exception.BusinessException;
import com.example.profile.dto.response.AddressResponse;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.dto.response.internal.ProfileSnapshotResponse;
import com.example.profile.entity.Profiles;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.ProfileProjectionRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileQueryService {
    private final ProfileRepository profileRepository;
    private final ProfileAddressRepository profileAddressRepository;
    private final ProfileProjectionRepairService profileProjectionRepairService;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId){
        Profiles profile = profileProjectionRepairService.ensureProfileWithImage(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        return ProfileResponse.from(
            profile,
            profileAddressRepository.findByProfileIdAndIsDefaultTrue(userId)
        );
    }

    @Transactional(readOnly = true)
    public ProfileSnapshotResponse getProfileSnapshot(Long userId) {
        Profiles profile = profileProjectionRepairService.ensureProfileWithImage(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        return new ProfileSnapshotResponse(
            profile.getUserId(),
            profile.getNickname(),
            profile.getProfileImage() == null ? null : profile.getProfileImage().getMediaId()
        );
    }

    @Transactional(readOnly = true)
    public List<Profiles> getProfileList(List<Long> userIds){
        return profileRepository.findAllByUserIdIn(userIds);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(Long userId) {
        profileProjectionRepairService.ensureProfile(userId);
        return profileAddressRepository.findProfileAddressByProfileId(userId)
            .stream()
            .map(AddressResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileAddressResponse> getProfileOfDeliveryAddress(Long userId){
        profileProjectionRepairService.ensureProfile(userId);
        return profileAddressRepository.findProfileAddressByProfileId(userId)
            .stream()
            .map(ProfileAddressResponse::from)
            .toList();
    }

}
