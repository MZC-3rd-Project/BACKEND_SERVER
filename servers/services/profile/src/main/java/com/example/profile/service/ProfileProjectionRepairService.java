package com.example.profile.service;

import com.example.clients.auth.facade.AuthItemQueryClientFacade;
import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.command.ProfileProjectionSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileProjectionRepairService {

    private final ProfileRepository profileRepository;
    private final AuthItemQueryClientFacade authItemQueryClientFacade;
    private final ProfileProjectionSyncService profileProjectionSyncService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Profiles> ensureProfile(Long userId) {
        Optional<Profiles> existing = profileRepository.findByUserId(userId);
        return existing.isPresent() ? existing : repair(userId, () -> profileRepository.findByUserId(userId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Profiles> ensureProfileWithImage(Long userId) {
        Optional<Profiles> existing = profileRepository.findByUserIdWithImage(userId);
        return existing.isPresent() ? existing : repair(userId, () -> profileRepository.findByUserIdWithImage(userId));
    }

    private Optional<Profiles> repair(Long userId, Supplier<Optional<Profiles>> loader) {
        if (userId == null || userId <= 0L) {
            return Optional.empty();
        }

        JsonNode userInfo = authItemQueryClientFacade.findProfileInfo(userId);
        if (userInfo == null || userInfo.isMissingNode() || userInfo.isNull()) {
            log.warn("[ProfileProjectionRepair] auth source not found. userId={}", userId);
            return Optional.empty();
        }

        String email = textValue(userInfo, "email");
        String nickname = textValue(userInfo, "nickname");
        if (!StringUtils.hasText(email) || !StringUtils.hasText(nickname)) {
            log.warn("[ProfileProjectionRepair] auth source incomplete. userId={}", userId);
            return Optional.empty();
        }

        profileProjectionSyncService.upsertFromUserCreated(userId, email, nickname);
        return loader.get();
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        String value = field.asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
