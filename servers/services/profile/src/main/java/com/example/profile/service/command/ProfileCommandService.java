package com.example.profile.service.command;

import com.example.clients.auth.dto.profile.AuthSyncQuery;
import com.example.core.exception.BusinessException;
import com.example.event.EventMetadata;
import com.example.event.EventPublisher;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.event.ProfileUpdatedEvent;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.ProfileProjectionRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileCommandService {

    private final ProfileImageRepository profilesImageRepository;
    private final ProfileRepository profileRepository;
    private final ProfileAddressRepository profileAddressRepository;
    private final ProfileMediaReferenceService profileMediaReferenceService;
    private final ProfileProjectionRepairService profileProjectionRepairService;
    private final EventPublisher eventPublisher;

    @Transactional
    public void createProfile(AuthSyncQuery req) {
        if (profileRepository.existsByNickname(req.profileInfo().nickname())) {
            throw new BusinessException(ProfileErrorCode.PROFILE_ALREADY_NICKNAME);
        }

        Profiles profile = profileRepository.save(Profiles.create(req));
        profilesImageRepository.save(ProfilesImage.createDefault(profile));

        if (req.profileDeliveryInfo() != null && !req.profileDeliveryInfo().isEmpty()) {
            List<ProfileAddress> addresses = req.profileDeliveryInfo().stream()
                .map(addr -> ProfileAddress.create(profile.getUserId(), addr))
                .toList();
            profileAddressRepository.saveAll(addresses);
        }

        log.info("Profile created. userId={}", profile.getUserId());
    }

    @Transactional
    public void updateProfile(ProfileRequest req, Long userId) {
        Profiles profile = profileProjectionRepairService.ensureProfile(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        Long canonicalMediaId = profileMediaReferenceService.resolveCanonicalMediaId(req.getMediaId(), req.getMediaRef());
        profileMediaReferenceService.validateReadableMedia(canonicalMediaId);

        ProfilesImage profileImage = profilesImageRepository.findByUserId(userId)
            .orElseGet(() -> profilesImageRepository.save(ProfilesImage.createDefault(profile)));
        profileImage.updateMediaId(canonicalMediaId);

        validateNicknameAvailability(req, profile);
        profile.updateProfile(req);
        profileMediaReferenceService.syncProfileImageLink(userId, canonicalMediaId);

        eventPublisher.publish(
            new ProfileUpdatedEvent(
                profile.getId(),
                profile.getUserId(),
                profile.getEmail(),
                profile.getNickname(),
                profile.getPhoneNumber(),
                canonicalMediaId
            ),
            EventMetadata.of("PROFILE", String.valueOf(profile.getId()))
        );

        log.info("Profile updated. userId={}, profileId={}, mediaLinked={}",
            userId, profile.getId(), canonicalMediaId != null);
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        Profiles profile = profileProjectionRepairService.ensureProfile(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        ProfileAddress target = profileAddressRepository.findById(addressId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.ADDRESS_NOT_FOUND));

        if (!target.getProfileId().equals(userId)) {
            throw new BusinessException(ProfileErrorCode.ADDRESS_UNAUTHORIZED);
        }

        profileAddressRepository.findByProfileIdAndIsDefaultTrue(userId)
            .ifPresent(ProfileAddress::unsetDefault);

        target.setAsDefault();
        log.info("Default profile address updated. userId={}, addressId={}", userId, addressId);
    }

    private void validateNicknameAvailability(ProfileRequest req, Profiles profile) {
        if (!StringUtils.hasText(req.getNickname())) {
            return;
        }

        if (profileRepository.existsByNickname(req.getNickname())
            && !Objects.equals(req.getNickname(), profile.getNickname())) {
            throw new BusinessException(ProfileErrorCode.PROFILE_ALREADY_NICKNAME);
        }
    }
}
