package com.example.profile.config;

import com.example.profile.service.command.ProfileProjectionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Profile("develop")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.profile.dummy-bootstrap", name = "enabled", havingValue = "true")
public class ProfileDummyBootstrapRunner implements ApplicationRunner {

    private final ProfileDummyBootstrapProperties properties;
    private final ProfileProjectionSyncService profileProjectionSyncService;

    @Override
    public void run(ApplicationArguments args) {
        Long userId = properties.getUserId() != null ? properties.getUserId() : 9000001L;
        String email = StringUtils.hasText(properties.getEmail())
                ? properties.getEmail()
                : "dummy-profile-%d@dev.local".formatted(userId);
        String nickname = StringUtils.hasText(properties.getNickname())
                ? properties.getNickname()
                : "dev-user-%d".formatted(userId);

        profileProjectionSyncService.upsertFromUserCreated(userId, email, nickname);
        log.info("[ProfileDummyBootstrap] ensured dummy profile. userId={}, email={}", userId, email);
    }
}
