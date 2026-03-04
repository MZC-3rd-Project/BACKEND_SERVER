package com.example.profile.service.command;

import com.example.profile.entity.Profiles;
import com.example.profile.entity.ProfilesImage;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileProjectionSyncService {

    private final ProfileRepository profileRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileMediaReferenceService profileMediaReferenceService;

    @Transactional
    public void upsertFromUserCreated(Long userId, String email, String nickname) {
        Profiles profile = profileRepository.findByUserId(userId)
                .map(existing -> {
                    existing.applyUserCreatedProjection(email, nickname);
                    return existing;
                })
                .orElseGet(() -> profileRepository.save(Profiles.createProjection(userId, email, nickname)));

        profileImageRepository.findByUserId(userId)
                .orElseGet(() -> profileImageRepository.save(ProfilesImage.createDefault(profile)));

        log.info("[ProfileProjectionSync] user projection upsert completed. userId={}", userId);
    }

    @Transactional
    public void applyUserEmailChanged(Long userId, String newEmail) {
        profileRepository.findByUserId(userId)
                .ifPresentOrElse(profile -> {
                    profile.applyEmailChangedProjection(newEmail);
                    log.info("[ProfileProjectionSync] email projection updated. userId={}", userId);
                }, () -> log.warn("[ProfileProjectionSync] email change skipped. profile not found. userId={}", userId));
    }

    @Transactional
    public void withdrawProjection(Long userId) {
        profileMediaReferenceService.clearProfileLinks(userId);
        profileRepository.findByUserIdWithImage(userId)
                .ifPresentOrElse(profile -> {
                    profile.softDelete();
                    ProfilesImage image = profile.getProfileImage();
                    if (image != null) {
                        image.updateMediaId(null);
                    }
                    log.info("[ProfileProjectionSync] user projection withdrawn. userId={}", userId);
                }, () -> log.warn("[ProfileProjectionSync] profile not found during withdraw projection. links cleared only. userId={}", userId));
    }
}
