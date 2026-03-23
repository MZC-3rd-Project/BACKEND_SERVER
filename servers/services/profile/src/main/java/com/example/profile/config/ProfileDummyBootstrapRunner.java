package com.example.profile.config;

import com.example.profile.dto.request.ProfileRequest;
import com.example.profile.entity.ProfileAddress;
import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfileAddressRepository;
import com.example.profile.repository.ProfileRepository;
import com.example.profile.service.command.ProfileProjectionSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@Profile("develop")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.profile.dummy-bootstrap", name = "enabled", havingValue = "true")
public class ProfileDummyBootstrapRunner implements ApplicationRunner {

    private final ProfileDummyBootstrapProperties properties;
    private final ProfileProjectionSyncService profileProjectionSyncService;
    private final ProfileRepository profileRepository;
    private final ProfileAddressRepository profileAddressRepository;

    @Override
    public void run(ApplicationArguments args) {
        Long userId = properties.getUserId() != null ? properties.getUserId() : 9000001L;
        String email = StringUtils.hasText(properties.getEmail())
                ? properties.getEmail()
                : "dummy-profile-%d@dev.local".formatted(userId);
        String nickname = StringUtils.hasText(properties.getNickname())
                ? properties.getNickname()
                : "dev-user-%d".formatted(userId);
        String phone = StringUtils.hasText(properties.getPhone())
                ? properties.getPhone()
                : "010-0000-0001";

        profileProjectionSyncService.upsertFromUserCreated(userId, email, nickname);

        profileRepository.findByUserId(userId).ifPresent(profile -> {
            applyPhone(profile, phone);
            ensureDummyAddresses(profile);
        });

        log.info("[ProfileDummyBootstrap] ensured dummy profile. userId={}, email={}", userId, email);
    }

    private void applyPhone(Profiles profile, String phone) {
        ProfileRequest req = ProfileRequest.builder().phone(phone).build();
        profile.updateProfile(req);
        profileRepository.save(profile);
    }

    private void ensureDummyAddresses(Profiles profile) {
        Long userId = profile.getUserId();
        List<ProfileAddress> existing = profileAddressRepository.findProfileAddressByProfileId(userId);
        if (!existing.isEmpty()) {
            return;
        }

        ProfileAddress home = ProfileAddress.builder()
                .profileId(userId)
                .deliveryName("집")
                .zipcode("06236")
                .sido("서울특별시")
                .sigungu("강남구")
                .roadName("테헤란로")
                .buildingNumber("427")
                .buildingName("위워크타워")
                .detailAddress("10층 1001호")
                .sortOrder(0)
                .isDefault(true)
                .build();

        ProfileAddress office = ProfileAddress.builder()
                .profileId(userId)
                .deliveryName("회사")
                .zipcode("03181")
                .sido("서울특별시")
                .sigungu("종로구")
                .roadName("청계천로")
                .buildingNumber("1")
                .buildingName("광화문빌딩")
                .detailAddress("3층 302호")
                .sortOrder(1)
                .isDefault(false)
                .build();

        ProfileAddress other = ProfileAddress.builder()
                .profileId(userId)
                .deliveryName("부모님댁")
                .zipcode("48058")
                .sido("부산광역시")
                .sigungu("해운대구")
                .roadName("해운대해변로")
                .buildingNumber("264")
                .buildingName("해운대아파트")
                .detailAddress("101동 501호")
                .sortOrder(2)
                .isDefault(false)
                .build();

        profileAddressRepository.saveAll(List.of(home, office, other));
        log.info("[ProfileDummyBootstrap] created 3 dummy addresses. userId={}", userId);
    }
}
