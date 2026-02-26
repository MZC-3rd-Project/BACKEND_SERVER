package com.example.profile.service.command;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.profile.dto.request.ProfileCreateRequest;
import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileCommandService {

    private final ProfileImageRepository profilesImageRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public Profiles createProfile(ProfileCreateRequest req)
    {
        if(profileRepository.existsByNickname(req.getNickname()))
        {
            throw new BusinessException(ProfileErrorCode.PROFILE_ALREADY_NICKNAME);
        }
        Profiles profile = Profiles.create(req);
        return profileRepository.save(profile);
    }

    @Transactional
    public void updateProfile(ProfileRequest req, Long userId) {
        Profiles profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));
        ProfilesImage findUser = profilesImageRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_IMAGE_NOT_FOUND));

        if(req.getMediaId() != null){
            findUser.updateMediaId(req.getMediaId());
        } else {
            findUser.updateMediaId(null);

        }

        if(existsMyNickname(req, profile)){
            profile.updateProfile(req);
        } else {
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    private boolean existsMyNickname(ProfileRequest req, Profiles profile) {
        if(profileRepository.existsByNickname(req.getNickname()) && !Objects.equals(req.getNickname(), profile.getNickname())) {
            throw new BusinessException(ProfileErrorCode.PROFILE_ALREADY_NICKNAME);
        }
        return true;
    }


}
