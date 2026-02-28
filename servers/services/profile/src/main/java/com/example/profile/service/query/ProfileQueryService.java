package com.example.profile.service.query;

import com.example.core.exception.BusinessException;
import com.example.profile.dto.response.ProfileResponse;
import com.example.profile.entity.Profiles;
import com.example.profile.exception.ProfileErrorCode;
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

    public ProfileResponse getProfile(Long userId){
        Profiles profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));

        return ProfileResponse.from(profile);
    }

    @Transactional
    public List<Profiles> getProfileList(List<Long> userIds){
        return profileRepository.findAllByUserIdIn(userIds);
    }
}
