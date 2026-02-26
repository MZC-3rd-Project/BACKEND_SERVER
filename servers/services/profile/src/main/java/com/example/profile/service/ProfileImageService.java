package com.example.profile.service;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.profile.dto.response.ProfileImageResponse;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.exception.ProfileErrorCode;
import com.example.profile.repository.ProfileImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileImageService {

    private final ProfileImageRepository profilesImageRepository;

    public ProfilesImage createProfileImage(Long userid, Long mediaId) {

        try {
            ProfilesImage findProfileImage = profilesImageRepository.findByProfileId(userid);
            if (findProfileImage != null) {
                ProfilesImage profilesImage = ProfilesImage.create(mediaId);
                return profilesImageRepository.save(profilesImage);
            } else {
                log.error("not found profile image");
                throw new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND);
            }
        } catch (Exception e){
            log.warn(e.getMessage());
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }

    };


}
