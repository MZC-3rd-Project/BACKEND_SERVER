package com.example.profile.service.command;

import com.example.profile.entity.Profiles;
import com.example.profile.repository.ProfileImageRepository;
import com.example.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ProfileProjectionSyncService.class, ProfileProjectionSyncServiceIntegrationTest.JpaAuditTestConfig.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProfileProjectionSyncServiceIntegrationTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditTestConfig {
    }

    @Autowired
    private ProfileProjectionSyncService profileProjectionSyncService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ProfileImageRepository profileImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProfileMediaReferenceService profileMediaReferenceService;

    @Test
    void upsertFromUserCreated_updatesExistingProjectionConsistently() {
        Long userId = 7001L;

        Profiles existing = profileRepository.save(Profiles.createProjection(userId, "first@example.com", "firstNick"));
        jdbcTemplate.update(
                "insert into profile_images (user_id, media_id, sort_order, created_at, updated_at) values (?, ?, ?, current_timestamp, current_timestamp)",
                userId,
                null,
                0
        );

        profileProjectionSyncService.upsertFromUserCreated(userId, "updated@example.com", "updatedNick");

        Profiles updatedProjection = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(updatedProjection.getId()).isEqualTo(existing.getId());
        assertThat(updatedProjection.getEmail()).isEqualTo("updated@example.com");
        assertThat(updatedProjection.getNickname()).isEqualTo("updatedNick");
        assertThat(profileRepository.findAll().stream().filter(profile -> userId.equals(profile.getUserId())).count())
                .isEqualTo(1L);
        assertThat(profileImageRepository.findByUserId(userId)).isPresent();
    }
}
