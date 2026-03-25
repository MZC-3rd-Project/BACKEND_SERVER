package com.example.profile.service.query;

import com.example.clients.media.facade.MediaClientFacade;
import com.example.core.exception.BusinessException;
import com.example.profile.dto.response.AddressResponse;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.dto.response.internal.ProfileSnapshotResponse;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.entity.Profiles;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileImageRepository;
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
    private final ProfileImageRepository profileImageRepository;
    private final ProfileProjectionRepairService profileProjectionRepairService;
    private final MediaClientFacade mediaClientFacade;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId){
        Profiles profile = profileProjectionRepairService.ensureProfileWithImage(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        Long mediaId = profileImageRepository.findByUserId(userId)
            .map(ProfilesImage::getMediaId)
            .orElse(null);
        return ProfileResponse.from(
            profile,
            profileAddressRepository.findByProfileIdAndIsDefaultTrue(userId),
            resolveMediaUrl(mediaId)
        );
    }

    @Transactional(readOnly = true)
    public ProfileSnapshotResponse getProfileSnapshot(Long userId) {
        Profiles profile = profileProjectionRepairService.ensureProfileWithImage(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        Long mediaId = profileImageRepository.findByUserId(userId)
            .map(ProfilesImage::getMediaId)
            .orElse(null);
        return new ProfileSnapshotResponse(
            profile.getUserId(),
            profile.getNickname(),
            mediaId
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

    private String resolveMediaUrl(Long mediaId) {
        if (mediaId == null || mediaId <= 0L) {
            return null;
        }
        try {
            return mediaClientFacade.getMediaUrl(mediaId);
        } catch (RuntimeException exception) {
            log.warn("Profile media url resolution failed. mediaId={}", mediaId, exception);
            return null;
        }
    }

}
