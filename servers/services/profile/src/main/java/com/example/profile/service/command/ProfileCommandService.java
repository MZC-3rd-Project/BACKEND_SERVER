package com.example.profile.service.command;

import com.example.core.exception.BusinessException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileCommandService {

    private final ProfileImageRepository profilesImageRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public void updateProfile(ProfileRequest req) {
        log.info("mediaid : {}, userid: {}", req.getMediaId(),req.getUserId());

        uploadProfileImage(req.getUserId(), req.getMediaId());


        Profiles profile = profileRepository.findByUserId(req.getUserId())
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_NOT_FOUND));
        profile.updateProfile(req.getEmail(),req.getNickname(), req.getPhone(), req.getDelivery());
    };

    private void uploadProfileImage(Long userId, Long mediaId){

        ProfilesImage profileImage = profilesImageRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_IMAGE_NOT_FOUND));
        profileImage.updateMediaId(mediaId);
    }


}
