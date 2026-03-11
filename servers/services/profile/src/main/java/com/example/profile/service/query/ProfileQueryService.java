package com.example.profile.service.query;

import com.example.core.exception.BusinessException;
import com.example.profile.dto.response.ProfileAddressResponse;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.entity.Profiles;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileRepository;
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

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId){
        Profiles profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        return ProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public List<Profiles> getProfileList(List<Long> userIds){
        return profileRepository.findAllByUserIdIn(userIds);
    }

    @Transactional(readOnly = true)
    public List<ProfileAddressResponse> getProfileOfDeliveryAddress(Long userId){
        return profileAddressRepository.findProfileAddressByProfileId(userId)
            .stream()
            .map(ProfileAddressResponse::from)
            .toList();
    }

}
